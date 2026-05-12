package com.afternote.domain.diary.service;

import com.afternote.domain.diary.dto.DiaryCreateRequest;
import com.afternote.domain.diary.dto.DiaryListResponse;
import com.afternote.domain.diary.dto.DiaryResponse;
import com.afternote.domain.diary.dto.DiaryUpdateRequest;
import com.afternote.domain.diary.model.Diary;
import com.afternote.domain.diary.model.TodayMood;
import com.afternote.domain.diary.repository.DiaryRepository;
import com.afternote.domain.mindrecord.emotion.event.DiaryEmotionAnalysisRequestedEvent;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import com.afternote.global.sanitizer.MindRecordHtmlSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final MindRecordHtmlSanitizer mindRecordHtmlSanitizer;

    @Transactional
    public DiaryResponse createDiary(Long userId, DiaryCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Diary diary = Diary.create(
                user,
                request.getTitle(),
                mindRecordHtmlSanitizer.sanitize(request.getContent()),
                request.getIsDraft(),
                request.getImageUrl(),
                request.getTodayMood()
        );

        Diary saved = diaryRepository.save(diary);
        if (Boolean.FALSE.equals(saved.getIsDraft())) {
            eventPublisher.publishEvent(new DiaryEmotionAnalysisRequestedEvent(userId, saved.getId()));
        }

        return DiaryResponse.from(saved);
    }

    public DiaryListResponse getDiariesByDate(Long userId, LocalDate date, Boolean draftOnly) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        List<Diary> diaries = Boolean.TRUE.equals(draftOnly)
                ? diaryRepository.findByUserIdAndIsDraftTrueAndCreatedAtBetweenOrderByCreatedAtDesc(userId, start, end)
                : diaryRepository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(userId, start, end);

        List<DiaryResponse> responseList = diaries.stream()
                .map(DiaryResponse::from)
                .toList();

        LocalDate today = LocalDate.now();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime monthEnd = today.plusMonths(1).withDayOfMonth(1).atStartOfDay();
        long thisMonthCount = diaryRepository.countByUserIdAndIsDraftFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                userId, monthStart, monthEnd);

        LocalDateTime weekStart = today.minusDays(6).atStartOfDay();
        LocalDateTime weekEnd = today.plusDays(1).atStartOfDay();
        List<TodayMood> weekMoods = diaryRepository.findTodayMoodsByUserIdAndCreatedAtRange(userId, weekStart, weekEnd);
        TodayMood weeklyDominant = dominantMood(weekMoods);

        return DiaryListResponse.from(responseList, thisMonthCount, weeklyDominant);
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

        diary.update(
                request.getTitle(),
                request.getContent() != null ? mindRecordHtmlSanitizer.sanitize(request.getContent()) : null,
                request.getIsDraft(),
                request.getImageUrl(),
                request.getTodayMood()
        );
        if (Boolean.FALSE.equals(diary.getIsDraft())) {
            eventPublisher.publishEvent(new DiaryEmotionAnalysisRequestedEvent(userId, diary.getId()));
        }

        return DiaryResponse.from(diary);
    }

    @Transactional
    public void deleteDiary(Long userId, Long diaryId) {
        Diary diary = diaryRepository.findByIdAndUserId(diaryId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.DIARY_NOT_FOUND));
        diaryRepository.delete(diary);
    }
}
