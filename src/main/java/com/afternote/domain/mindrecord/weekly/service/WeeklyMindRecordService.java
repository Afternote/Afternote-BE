package com.afternote.domain.mindrecord.weekly.service;

import com.afternote.domain.dailyquestion.model.UserDailyQuestion;
import com.afternote.domain.dailyquestion.repository.UserDailyQuestionRepository;
import com.afternote.domain.deepthought.model.DeepThought;
import com.afternote.domain.deepthought.repository.DeepThoughtRepository;
import com.afternote.domain.diary.model.Diary;
import com.afternote.domain.diary.repository.DiaryRepository;
import com.afternote.domain.mindrecord.emotion.model.Emotion;
import com.afternote.domain.mindrecord.emotion.model.EmotionSourceType;
import com.afternote.domain.mindrecord.emotion.repository.EmotionRepository;
import com.afternote.domain.mindrecord.weekly.dto.WeeklyMindRecordResponse;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
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
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WeeklyMindRecordService {

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

        Map<Long, String> diaryEmotion = emotionMap(
                userId, EmotionSourceType.DIARY, diaries.stream().map(Diary::getId).toList());
        Map<Long, String> dqEmotion = emotionMap(
                userId, EmotionSourceType.DAILY_QUESTION, dailyQuestions.stream().map(UserDailyQuestion::getId).toList());
        Map<Long, String> dtEmotion = emotionMap(
                userId, EmotionSourceType.DEEP_THOUGHT, deepThoughts.stream().map(DeepThought::getId).toList());

        List<WeeklyEmotionItem> topEmotions = buildTopEmotions(userId, diaries, dailyQuestions, deepThoughts);
        String keywordJson = toKeywordJson(topEmotions);

        String summaryText = geminiService.generateWeeklyMindRecordSummary(keywordJson);
        if (summaryText == null || summaryText.isBlank()) {
            summaryText = "이번 주 기록을 바탕으로 인사이트를 준비 중이에요. 꾸준히 남겨 주셔서 고마워요.";
        }

        persistWeeklyReport(user, storedStart, storedEnd, summaryText, keywordJson);

        List<WeekRecordItem> week = buildWeekItems(diaries, dailyQuestions, deepThoughts, diaryEmotion, dqEmotion, dtEmotion);

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
                .build();
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

    private Map<Long, String> emotionMap(Long userId, EmotionSourceType type, List<Long> sourceIds) {
        if (sourceIds.isEmpty()) {
            return Map.of();
        }
        return emotionRepository.findByUserIdAndSourceTypeAndSourceIdIn(userId, type, sourceIds).stream()
                .collect(Collectors.toMap(Emotion::getSourceId, Emotion::getEmotionCategory, (a, b) -> a));
    }

    private List<WeeklyEmotionItem> buildTopEmotions(
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
            collected.addAll(emotionRepository.findByUserIdAndSourceTypeAndSourceIdIn(userId, EmotionSourceType.DIARY, dIds));
        }
        if (!qIds.isEmpty()) {
            collected.addAll(emotionRepository.findByUserIdAndSourceTypeAndSourceIdIn(userId, EmotionSourceType.DAILY_QUESTION, qIds));
        }
        if (!tIds.isEmpty()) {
            collected.addAll(emotionRepository.findByUserIdAndSourceTypeAndSourceIdIn(userId, EmotionSourceType.DEEP_THOUGHT, tIds));
        }

        Map<String, Integer> freq = new HashMap<>();
        for (Emotion e : collected) {
            String k = e.getEmotionCategory();
            if (k == null || k.isBlank()) {
                continue;
            }
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

    private List<WeekRecordItem> buildWeekItems(
            List<Diary> diaries,
            List<UserDailyQuestion> dailyQuestions,
            List<DeepThought> deepThoughts,
            Map<Long, String> diaryEmotion,
            Map<Long, String> dqEmotion,
            Map<Long, String> dtEmotion
    ) {
        record Sortable(LocalDateTime at, WeekRecordItem item) {
        }

        List<Sortable> sortables = new ArrayList<>();

        for (Diary d : diaries) {
            LocalDateTime at = d.getCreatedAt() != null ? d.getCreatedAt() : d.getUpdatedAt();
            String analyzed = diaryEmotion.get(d.getId());
            String emotion = analyzed != null && !analyzed.isBlank()
                    ? analyzed
                    : (d.getTodayMood() != null ? d.getTodayMood().name() : null);
            sortables.add(new Sortable(at, WeekRecordItem.builder()
                    .diaryId(d.getId())
                    .day(at.toLocalDate().getDayOfMonth())
                    .isDiary(true)
                    .emotion(emotion)
                    .build()));
        }

        for (UserDailyQuestion u : dailyQuestions) {
            LocalDateTime at = u.getCreatedAt() != null ? u.getCreatedAt() : u.getQuestionDate().atStartOfDay();
            String emotion = dqEmotion.get(u.getId());
            sortables.add(new Sortable(at, WeekRecordItem.builder()
                    .diaryId(u.getId())
                    .day(u.getQuestionDate().getDayOfMonth())
                    .isDiary(false)
                    .emotion(emotion)
                    .build()));
        }

        for (DeepThought dt : deepThoughts) {
            LocalDateTime at = dt.getCreatedAt() != null ? dt.getCreatedAt() : dt.getUpdatedAt();
            sortables.add(new Sortable(at, WeekRecordItem.builder()
                    .diaryId(dt.getId())
                    .day(at.toLocalDate().getDayOfMonth())
                    .isDiary(false)
                    .emotion(dtEmotion.get(dt.getId()))
                    .build()));
        }

        return sortables.stream()
                .sorted(Comparator.comparing(Sortable::at))
                .map(Sortable::item)
                .toList();
    }
}
