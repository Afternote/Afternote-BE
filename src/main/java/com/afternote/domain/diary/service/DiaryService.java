package com.afternote.domain.diary.service;

import com.afternote.domain.diary.dto.DiaryCreateRequest;
import com.afternote.domain.diary.dto.DiaryListResponse;
import com.afternote.domain.diary.dto.DiaryResponse;
import com.afternote.domain.diary.dto.DiaryUpdateRequest;
import com.afternote.domain.diary.model.Diary;
import com.afternote.domain.diary.model.TodayMood;
import com.afternote.domain.diary.repository.DiaryRepository;
import com.afternote.domain.mindrecord.emotion.EmotionAnalysisTrigger;
import com.afternote.domain.mindrecord.emotion.event.DiaryEmotionAnalysisRequestedEvent;
import com.afternote.domain.receiver.dto.MindRecordReceiverSummaryResponse;
import com.afternote.domain.receiver.repository.DiaryReceiverRepository;
import com.afternote.domain.receiver.service.MindRecordReceiverService;
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
import java.time.LocalDateTime;
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

    private final DiaryRepository diaryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;
    private final MindRecordContentMediaService mindRecordContentMediaService;
    private final DiaryReceiverRepository diaryReceiverRepository;
    private final MindRecordReceiverService mindRecordReceiverService;

    @Transactional
    public DiaryResponse createDiary(Long userId, DiaryCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        user.touchActivity();

        Diary diary = Diary.create(
                user,
                request.getTitle(),
                mindRecordContentMediaService.prepareContentForSave(userId, request.getContent()),
                request.getIsDraft(),
                request.getTodayMood()
        );

        Diary saved = diaryRepository.save(diary);
        if (Boolean.FALSE.equals(saved.getIsDraft())) {
            eventPublisher.publishEvent(new DiaryEmotionAnalysisRequestedEvent(userId, saved.getId()));
        }

        List<MindRecordReceiverSummaryResponse> receivers = mindRecordReceiverService.replaceDiaryReceivers(
                userId,
                saved,
                request.getReceiverIds(),
                false
        );
        return DiaryResponse.from(saved, receivers);
    }

    public DiaryListResponse getDiariesByMonth(Long userId, YearMonth yearMonth, Boolean draftOnly) {
        LocalDateTime monthStart = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = yearMonth.plusMonths(1).atDay(1).atStartOfDay();

        List<Diary> diaries = Boolean.TRUE.equals(draftOnly)
                ? diaryRepository.findByUserIdAndIsDraftTrueAndCreatedAtBetweenOrderByCreatedAtDesc(userId, monthStart, monthEnd)
                : diaryRepository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(userId, monthStart, monthEnd);

        List<Long> diaryIds = diaries.stream().map(Diary::getId).toList();
        Map<Long, List<MindRecordReceiverSummaryResponse>> receiversMap =
                mindRecordReceiverService.getDiaryReceiversMap(diaryIds);

        List<DiaryResponse> responseList = diaries.stream()
                .map(diary -> DiaryResponse.from(
                        diary,
                        receiversMap.getOrDefault(diary.getId(), List.of())
                ))
                .toList();

        long monthDiaryCount = diaryRepository.countByUserIdAndIsDraftFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                userId, monthStart, monthEnd);

        LocalDate today = LocalDate.now();
        LocalDateTime weekStart = today.minusDays(6).atStartOfDay();
        LocalDateTime weekEnd = today.plusDays(1).atStartOfDay();
        List<TodayMood> weekMoods = diaryRepository.findTodayMoodsByUserIdAndCreatedAtRange(userId, weekStart, weekEnd);
        TodayMood weeklyDominant = dominantMood(weekMoods);

        return DiaryListResponse.from(yearMonth, responseList, monthDiaryCount, weeklyDominant);
    }

    private static TodayMood dominantMood(List<TodayMood> moods) {
        if (moods == null || moods.isEmpty()) {
            return null;
        }
        return moods.stream()
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

        String contentToUpdate = request.getContent() != null
                ? mindRecordContentMediaService.prepareContentForSave(userId, request.getContent())
                : null;

        diary.update(
                request.getTitle(),
                contentToUpdate,
                request.getIsDraft(),
                request.getTodayMood()
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
            eventPublisher.publishEvent(new DiaryEmotionAnalysisRequestedEvent(userId, diary.getId()));
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
    }
}
