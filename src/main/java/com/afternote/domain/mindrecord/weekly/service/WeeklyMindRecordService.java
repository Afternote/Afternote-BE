package com.afternote.domain.mindrecord.weekly.service;

import com.afternote.domain.dailyquestion.model.UserDailyQuestion;
import com.afternote.domain.dailyquestion.repository.UserDailyQuestionRepository;
import com.afternote.domain.deepthought.model.DeepThought;
import com.afternote.domain.deepthought.repository.DeepThoughtRepository;
import com.afternote.domain.diary.model.Diary;
import com.afternote.domain.diary.repository.DiaryRepository;
import com.afternote.domain.mindrecord.emotion.model.Emotion;
import com.afternote.domain.mindrecord.emotion.model.EmotionAnalysisStatus;
import com.afternote.domain.mindrecord.emotion.model.EmotionSourceType;
import com.afternote.domain.mindrecord.emotion.repository.EmotionRepository;
import com.afternote.domain.mindrecord.weekly.dto.WeeklyMindRecordResponse;
import com.afternote.domain.mindrecord.weekly.dto.WeeklyMindRecordResponse.EmotionAnalysisSummary;
import com.afternote.domain.mindrecord.weekly.dto.WeekRecordType;
import com.afternote.domain.mindrecord.weekly.dto.WeeklyMindRecordResponse.WeekRecordItem;
import com.afternote.domain.mindrecord.weekly.dto.WeeklyMindRecordResponse.WeeklyDailyQuestionItem;
import com.afternote.domain.mindrecord.weekly.dto.WeeklyMindRecordResponse.WeeklyEmotionItem;
import com.afternote.domain.mindrecord.weekly.model.WeeklyReport;
import com.afternote.domain.mindrecord.weekly.repository.WeeklyReportRepository;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import com.afternote.global.service.GeminiService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyMindRecordService {

    static final String FALLBACK_SUMMARY =
            "이번 주 기록을 바탕으로 인사이트를 준비 중이에요. 꾸준히 남겨 주셔서 고마워요.";

    private static final DateTimeFormatter DAILY_QUESTION_DATE =
            DateTimeFormatter.ofPattern("yyyy.MM.dd E", Locale.KOREAN);

    private final UserRepository userRepository;
    private final DiaryRepository diaryRepository;
    private final UserDailyQuestionRepository userDailyQuestionRepository;
    private final DeepThoughtRepository deepThoughtRepository;
    private final EmotionRepository emotionRepository;
    private final WeeklyReportRepository weeklyReportRepository;
    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;

    /** 동일 사용자·주차 동시 GET에서 Gemini를 한 번만 호출 */
    private final ConcurrentHashMap<String, Object> summaryLocks = new ConcurrentHashMap<>();

    @Transactional
    public WeeklyMindRecordResponse getWeeklyMindRecord(Long userId, LocalDate date) {
        if (date == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        LocalDate weekMonday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekSunday = weekMonday.plusDays(6);
        LocalDateTime rangeStart = weekMonday.atStartOfDay();
        LocalDateTime rangeEndExclusive = weekMonday.plusWeeks(1).atStartOfDay();
        LocalDateTime storedStart = rangeStart;
        LocalDateTime storedEnd = weekSunday.atTime(23, 59, 59);

        List<Diary> diaries = diaryRepository
                .findByUserIdAndIsDraftFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                        userId, rangeStart, rangeEndExclusive
                );

        List<UserDailyQuestion> dailyQuestionsRaw = userDailyQuestionRepository
                .findByUserIdAndQuestionDateBetweenOrderByQuestionDateAscCreatedAtAsc(userId, weekMonday, weekSunday);
        List<UserDailyQuestion> dailyQuestions = dailyQuestionsRaw.stream()
                .filter(udq -> udq.isAnswered() && !udq.isDraft())
                .toList();

        List<DeepThought> deepThoughts = deepThoughtRepository
                .findByUserIdAndIsDraftFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                        userId, rangeStart, rangeEndExclusive
                );

        List<Emotion> weekEmotions = collectWeekEmotions(userId, diaries, dailyQuestions, deepThoughts);
        List<WeeklyEmotionItem> topEmotions = buildTopEmotions(weekEmotions);
        EmotionAnalysisSummary emotionAnalysis = buildEmotionAnalysisSummary(
                diaries.size() + dailyQuestions.size() + deepThoughts.size(),
                weekEmotions
        );
        String keywordJson = toKeywordJson(topEmotions);

        String summaryText = resolveSummaryText(user, weekMonday, storedStart, storedEnd, keywordJson, topEmotions);

        // week[] 캘린더는 일기 todayMood 이모지용. Gemini 감정분석(emotions[])과 분리한다.
        List<WeekRecordItem> week = buildWeekItems(diaries, dailyQuestions, deepThoughts);

        List<WeeklyDailyQuestionItem> dqItems = dailyQuestions.stream()
                .map(udq -> WeeklyDailyQuestionItem.builder()
                        .title(udq.getDailyQuestion().getContent())
                        .content(udq.getContent())
                        .date(udq.getQuestionDate().format(DAILY_QUESTION_DATE))
                        .build())
                .toList();

        return WeeklyMindRecordResponse.builder()
                .dailyQuestionAmount(dailyQuestions.size())
                .diaryAmount(diaries.size())
                .deepThoughtAmount(deepThoughts.size())
                .summaryText(summaryText)
                .week(week)
                .dailyQuestion(dqItems)
                .emotions(topEmotions)
                .emotionAnalysis(emotionAnalysis)
                .build();
    }

    private String resolveSummaryText(
            User user,
            LocalDate weekMonday,
            LocalDateTime storedStart,
            LocalDateTime storedEnd,
            String keywordJson,
            List<WeeklyEmotionItem> topEmotions
    ) {
        String lockKey = user.getId() + ":" + weekMonday;
        Object lock = summaryLocks.computeIfAbsent(lockKey, k -> new Object());
        try {
            synchronized (lock) {
                Optional<WeeklyReport> existing =
                        weeklyReportRepository.findByUserIdAndStartDate(user.getId(), storedStart);

                if (existing.isPresent()
                        && sameKeywordJson(existing.get().getKeywordJson(), keywordJson)
                        && isUsableSummary(existing.get().getSummaryText())) {
                    log.info("[WeeklySummary] cache_hit userId={} week={}", user.getId(), weekMonday);
                    return existing.get().getSummaryText();
                }

                if (topEmotions == null || topEmotions.isEmpty()) {
                    log.debug("[WeeklySummary] skip_empty userId={} week={}", user.getId(), weekMonday);
                    if (existing.isPresent() && isUsableSummary(existing.get().getSummaryText())) {
                        return existing.get().getSummaryText();
                    }
                    return FALLBACK_SUMMARY;
                }

                String generated = geminiService.generateWeeklyMindRecordSummary(keywordJson);
                if (generated != null && !generated.isBlank() && !isFallbackSummary(generated)) {
                    log.info("[WeeklySummary] gemini_ok userId={} week={}", user.getId(), weekMonday);
                    persistWeeklyReport(user, storedStart, storedEnd, generated.trim(), keywordJson);
                    return generated.trim();
                }

                log.warn("[WeeklySummary] gemini_fail userId={} week={}", user.getId(), weekMonday);
                if (existing.isPresent() && isUsableSummary(existing.get().getSummaryText())) {
                    return existing.get().getSummaryText();
                }
                return FALLBACK_SUMMARY;
            }
        } finally {
            summaryLocks.remove(lockKey, lock);
        }
    }

    private static boolean isFallbackSummary(String summaryText) {
        return summaryText != null && FALLBACK_SUMMARY.equals(summaryText.trim());
    }

    private static boolean isUsableSummary(String summaryText) {
        return summaryText != null && !summaryText.isBlank() && !isFallbackSummary(summaryText);
    }

    /**
     * MySQL json 컬럼은 저장 시 공백/키 순서를 정규화하므로, 문자열 equals로는 캐시가 항상 미스난다.
     * 파싱한 JsonNode로 구조 동등성을 비교한다.
     */
    private boolean sameKeywordJson(String stored, String current) {
        if (Objects.equals(stored, current)) {
            return true;
        }
        if (stored == null || current == null || stored.isBlank() || current.isBlank()) {
            return false;
        }
        try {
            JsonNode a = objectMapper.readTree(stored);
            JsonNode b = objectMapper.readTree(current);
            return a.equals(b);
        } catch (JsonProcessingException e) {
            log.warn("[WeeklySummary] keywordJson parse failed stored={} current={}", stored, current);
            return false;
        }
    }

    private void persistWeeklyReport(
            User user,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String summaryText,
            String keywordJson
    ) {
        Optional<WeeklyReport> existing = weeklyReportRepository.findByUserIdAndStartDate(user.getId(), startDate);
        if (existing.isPresent()) {
            WeeklyReport r = existing.get();
            r.updateWeeklyContent(startDate, endDate, summaryText, keywordJson);
        } else {
            weeklyReportRepository.save(WeeklyReport.create(user, startDate, endDate, summaryText, keywordJson));
        }
    }

    private List<Emotion> collectWeekEmotions(
            Long userId,
            List<Diary> diaries,
            List<UserDailyQuestion> dailyQuestions,
            List<DeepThought> deepThoughts
    ) {
        List<Emotion> collected = new ArrayList<>();
        List<Long> dIds = diaries.stream().map(Diary::getId).toList();
        List<Long> qIds = dailyQuestions.stream().map(UserDailyQuestion::getId).toList();
        List<Long> tIds = deepThoughts.stream().map(DeepThought::getId).toList();
        if (!dIds.isEmpty()) {
            collected.addAll(emotionRepository.findByUserIdAndSourceTypeAndSourceIdIn(
                    userId, EmotionSourceType.DIARY, dIds));
        }
        if (!qIds.isEmpty()) {
            collected.addAll(emotionRepository.findByUserIdAndSourceTypeAndSourceIdIn(
                    userId, EmotionSourceType.DAILY_QUESTION, qIds));
        }
        if (!tIds.isEmpty()) {
            collected.addAll(emotionRepository.findByUserIdAndSourceTypeAndSourceIdIn(
                    userId, EmotionSourceType.DEEP_THOUGHT, tIds));
        }
        return collected;
    }

    private EmotionAnalysisSummary buildEmotionAnalysisSummary(int sourceTotal, List<Emotion> emotions) {
        int succeeded = 0;
        int pending = 0;
        int failed = 0;
        for (Emotion e : emotions) {
            EmotionAnalysisStatus status = e.effectiveStatus();
            if (status == EmotionAnalysisStatus.SUCCEEDED && e.isSucceeded()) {
                succeeded++;
            } else if (status == EmotionAnalysisStatus.FAILED) {
                failed++;
            } else {
                pending++;
            }
        }
        int missing = Math.max(0, sourceTotal - emotions.size());
        pending += missing;
        return EmotionAnalysisSummary.builder()
                .total(sourceTotal)
                .succeeded(succeeded)
                .pending(pending)
                .failed(failed)
                .build();
    }

    private List<WeeklyEmotionItem> buildTopEmotions(List<Emotion> collected) {
        Map<String, Integer> freq = new HashMap<>();
        for (Emotion e : collected) {
            if (!e.isSucceeded()) {
                continue;
            }
            String k = e.getEmotionCategory();
            freq.merge(k.trim(), 1, Integer::sum);
        }

        int total = freq.values().stream().mapToInt(Integer::intValue).sum();
        if (total <= 0) {
            return List.of();
        }

        return freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .map(e -> WeeklyEmotionItem.builder()
                        .keyword(e.getKey())
                        .percentage((int) Math.round(100.0 * e.getValue() / total))
                        .build())
                .toList();
    }

    private String toKeywordJson(List<WeeklyEmotionItem> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    /**
     * 주간 캘린더는 day(날짜)당 1개.
     * - 일기 있으면 → DIARY 우선, 같은 날 일기 여러 개면 최신(createdAt) todayMood
     * - 일기 없음 → 데일리질문/깊은생각 중 최신 1개 (emotion=null, 점 표시)
     */
    private List<WeekRecordItem> buildWeekItems(
            List<Diary> diaries,
            List<UserDailyQuestion> dailyQuestions,
            List<DeepThought> deepThoughts
    ) {
        record Candidate(LocalDate date, LocalDateTime at, WeekRecordItem item) {
        }

        Map<LocalDate, List<Candidate>> diariesByDate = new HashMap<>();
        Map<LocalDate, List<Candidate>> othersByDate = new HashMap<>();

        for (Diary d : diaries) {
            LocalDateTime at = d.getCreatedAt() != null ? d.getCreatedAt() : d.getUpdatedAt();
            if (at == null) {
                continue;
            }
            LocalDate date = at.toLocalDate();
            String todayMood = d.getTodayMood() != null ? d.getTodayMood().name() : null;
            diariesByDate.computeIfAbsent(date, ignored -> new ArrayList<>()).add(new Candidate(
                    date,
                    at,
                    WeekRecordItem.builder()
                            .diaryId(d.getId())
                            .day(date.getDayOfMonth())
                            .type(WeekRecordType.DIARY)
                            .emotion(todayMood)
                            .build()
            ));
        }

        for (UserDailyQuestion u : dailyQuestions) {
            LocalDateTime at = u.getCreatedAt() != null ? u.getCreatedAt() : u.getQuestionDate().atStartOfDay();
            LocalDate date = u.getQuestionDate();
            othersByDate.computeIfAbsent(date, ignored -> new ArrayList<>()).add(new Candidate(
                    date,
                    at,
                    WeekRecordItem.builder()
                            .diaryId(u.getId())
                            .day(date.getDayOfMonth())
                            .type(WeekRecordType.DAILY_QUESTION)
                            .emotion(null)
                            .build()
            ));
        }

        for (DeepThought dt : deepThoughts) {
            LocalDateTime at = dt.getCreatedAt() != null ? dt.getCreatedAt() : dt.getUpdatedAt();
            if (at == null) {
                continue;
            }
            LocalDate date = at.toLocalDate();
            othersByDate.computeIfAbsent(date, ignored -> new ArrayList<>()).add(new Candidate(
                    date,
                    at,
                    WeekRecordItem.builder()
                            .diaryId(dt.getId())
                            .day(date.getDayOfMonth())
                            .type(WeekRecordType.DEEP_THOUGHT)
                            .emotion(null)
                            .build()
            ));
        }

        return Stream.concat(diariesByDate.keySet().stream(), othersByDate.keySet().stream())
                .distinct()
                .sorted()
                .map(date -> {
                    List<Candidate> dayDiaries = diariesByDate.get(date);
                    if (dayDiaries != null && !dayDiaries.isEmpty()) {
                        // 최신 일기 1개 → 그 일기의 todayMood(이모지)
                        return dayDiaries.stream()
                                .max(Comparator.comparing(Candidate::at))
                                .map(Candidate::item)
                                .orElseThrow();
                    }
                    List<Candidate> dayOthers = othersByDate.getOrDefault(date, List.of());
                    return dayOthers.stream()
                            .max(Comparator.comparing(Candidate::at))
                            .map(Candidate::item)
                            .orElseThrow();
                })
                .toList();
    }
}
