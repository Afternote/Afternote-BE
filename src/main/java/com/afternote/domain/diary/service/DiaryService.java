package com.afternote.domain.diary.service;

import com.afternote.domain.diary.dto.DiaryCreateRequest;
import com.afternote.domain.diary.dto.DiaryListResponse;
import com.afternote.domain.diary.dto.DiaryResponse;
import com.afternote.domain.diary.dto.DiaryUpdateRequest;
import com.afternote.domain.diary.model.Diary;
import com.afternote.domain.diary.model.TodayMood;
import com.afternote.domain.diary.repository.DiaryRepository;
import com.afternote.domain.mindrecord.emotion.EmotionAnalysisPolicy;
import com.afternote.domain.mindrecord.emotion.EmotionAnalysisTrigger;
import com.afternote.domain.mindrecord.emotion.event.DiaryEmotionAnalysisRequestedEvent;
import com.afternote.domain.mindrecord.emotion.model.EmotionSourceType;
import com.afternote.domain.receiver.dto.MindRecordReceiverSummaryResponse;
import com.afternote.domain.receiver.repository.DiaryReceiverRepository;
import com.afternote.domain.receiver.service.MindRecordReceiverService;
import com.afternote.domain.user.event.UserActivityTouchedEvent;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import com.afternote.global.sanitizer.MindRecordContentMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiaryService {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final DiaryRepository diaryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;
    private final MindRecordContentMediaService mindRecordContentMediaService;
    private final DiaryReceiverRepository diaryReceiverRepository;
    private final MindRecordReceiverService mindRecordReceiverService;
    private final EmotionAnalysisPolicy emotionAnalysisPolicy;

    @Transactional
    public DiaryResponse createDiary(Long userId, DiaryCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (!Boolean.TRUE.equals(request.getIsDraft())) {
            validateForPublished(request.getTitle(), request.getContent(), request.getTodayMood());
        }

        LocalDate entryDate = resolveEntryDate(request.getDate());
        eventPublisher.publishEvent(new UserActivityTouchedEvent(userId));

        String content = mindRecordContentMediaService.prepareContentForSave(userId, request.getContent());
        Diary diary = Diary.create(
                user,
                emptyIfNull(request.getTitle()),
                emptyIfNull(content),
                request.getIsDraft(),
                request.getTodayMood(),
                entryDate
        );

        Diary saved = diaryRepository.save(diary);
        requestDiaryAnalysisIfAllowed(userId, saved);

        List<MindRecordReceiverSummaryResponse> receivers = mindRecordReceiverService.replaceDiaryReceivers(
                userId,
                saved,
                request.getReceiverIds(),
                false
        );
        return DiaryResponse.from(saved, receivers);
    }

    public DiaryListResponse getDiariesByMonth(Long userId, YearMonth yearMonth, Boolean draftOnly) {
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEndExclusive = yearMonth.plusMonths(1).atDay(1);

        List<Diary> diaries = Boolean.TRUE.equals(draftOnly)
                ? diaryRepository.findByUserIdAndIsDraftTrueAndEntryDateGreaterThanEqualAndEntryDateLessThanOrderByEntryDateDescCreatedAtDesc(
                        userId, monthStart, monthEndExclusive)
                : diaryRepository.findByUserIdAndEntryDateGreaterThanEqualAndEntryDateLessThanOrderByEntryDateDescCreatedAtDesc(
                        userId, monthStart, monthEndExclusive);

        List<Long> diaryIds = diaries.stream().map(Diary::getId).toList();
        Map<Long, List<MindRecordReceiverSummaryResponse>> receiversMap =
                mindRecordReceiverService.getDiaryReceiversMap(diaryIds);

        List<DiaryResponse> responseList = diaries.stream()
                .map(diary -> DiaryResponse.from(
                        diary,
                        receiversMap.getOrDefault(diary.getId(), List.of())
                ))
                .toList();

        long monthDiaryCount = diaryRepository.countByUserIdAndIsDraftFalseAndEntryDateGreaterThanEqualAndEntryDateLessThan(
                userId, monthStart, monthEndExclusive);

        LocalDate today = LocalDate.now(SEOUL);
        LocalDate weekStart = today.minusDays(6);
        LocalDate weekEndExclusive = today.plusDays(1);
        List<TodayMood> weekMoods = diaryRepository.findTodayMoodsByUserIdAndEntryDateRange(
                userId, weekStart, weekEndExclusive);
        TodayMood weeklyDominant = dominantMood(weekMoods);

        return DiaryListResponse.from(yearMonth, responseList, monthDiaryCount, weeklyDominant);
    }

    private static TodayMood dominantMood(List<TodayMood> moods) {
        if (moods == null || moods.isEmpty()) {
            return null;
        }
        return moods.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .max(Comparator.<Map.Entry<TodayMood, Long>>comparingLong(Map.Entry::getValue)
                        .thenComparing(e -> e.getKey().name()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    @Transactional
    public DiaryResponse updateDiary(Long userId, Long diaryId, DiaryUpdateRequest request) {
        Diary diary = diaryRepository.findByIdAndUserId(diaryId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.DIARY_NOT_FOUND));

        boolean wasDraft = Boolean.TRUE.equals(diary.getIsDraft());
        String beforeTitle = diary.getTitle();
        String beforeContent = diary.getContent();
        TodayMood beforeMood = diary.getTodayMood();

        boolean willBeDraft = request.getIsDraft() != null
                ? Boolean.TRUE.equals(request.getIsDraft())
                : Boolean.TRUE.equals(diary.getIsDraft());
        if (!willBeDraft) {
            String title = request.getTitle() != null ? request.getTitle() : diary.getTitle();
            String content = request.getContent() != null ? request.getContent() : diary.getContent();
            TodayMood mood = request.getTodayMood() != null ? request.getTodayMood() : diary.getTodayMood();
            validateForPublished(title, content, mood);
        }

        String contentToUpdate = request.getContent() != null
                ? mindRecordContentMediaService.prepareContentForSave(userId, request.getContent())
                : null;

        LocalDate entryDate = request.getDate() != null ? resolveEntryDate(request.getDate()) : null;

        diary.update(
                request.getTitle(),
                contentToUpdate,
                request.getIsDraft(),
                request.getTodayMood(),
                entryDate
        );

        boolean isFinal = Boolean.FALSE.equals(diary.getIsDraft());
        if (EmotionAnalysisTrigger.shouldAnalyzeDiary(
                wasDraft,
                isFinal,
                beforeTitle,
                beforeContent,
                beforeMood,
                diary.getTitle(),
                diary.getContent(),
                diary.getTodayMood()
        )) {
            requestDiaryAnalysisIfAllowed(userId, diary);
        }

        List<MindRecordReceiverSummaryResponse> receivers;
        if (request.getReceiverIds() != null) {
            receivers = mindRecordReceiverService.replaceDiaryReceivers(
                    userId,
                    diary,
                    request.getReceiverIds(),
                    false
            );
        } else {
            receivers = mindRecordReceiverService.getDiaryReceivers(diary.getId());
        }

        return DiaryResponse.from(diary, receivers);
    }

    @Transactional
    public void deleteDiary(Long userId, Long diaryId) {
        Diary diary = diaryRepository.findByIdAndUserId(diaryId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.DIARY_NOT_FOUND));
        diaryReceiverRepository.deleteByDiaryId(diaryId);
        diaryRepository.delete(diary);
        diaryRepository.flush();
    }

    private void requestDiaryAnalysisIfAllowed(Long userId, Diary diary) {
        if (!Boolean.FALSE.equals(diary.getIsDraft()) || diary.getId() == null) {
            return;
        }
        LocalDate recordDate = diary.getEntryDate() != null
                ? diary.getEntryDate()
                : LocalDate.now(SEOUL);
        if (!emotionAnalysisPolicy.allowAnalysis(userId, EmotionSourceType.DIARY, diary.getId(), recordDate)) {
            return;
        }
        eventPublisher.publishEvent(new DiaryEmotionAnalysisRequestedEvent(userId, diary.getId()));
    }

    /**
     * 정식 등록(isDraft=false)에서는 제목·본문·오늘의 기분이 모두 필요하다 (#132, #243).
     * Bean Validation 이 isDraft 를 보지 못하므로 서비스에서 분리한다.
     */
    private static void validateForPublished(String title, String content, TodayMood todayMood) {
        if (title == null || title.isBlank() || content == null || content.isBlank() || todayMood == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    /**
     * 미전송이면 오늘(Asia/Seoul). 미래 날짜는 거부. 과거는 제한 없음.
     */
    LocalDate resolveEntryDate(LocalDate requested) {
        LocalDate today = LocalDate.now(SEOUL);
        LocalDate date = requested != null ? requested : today;
        if (date.isAfter(today)) {
            throw new CustomException(ErrorCode.DIARY_INVALID_DATE);
        }
        return date;
    }
}
