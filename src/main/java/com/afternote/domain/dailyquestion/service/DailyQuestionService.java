package com.afternote.domain.dailyquestion.service;

import com.afternote.domain.dailyquestion.dto.*;
import com.afternote.domain.dailyquestion.model.DailyQuestion;
import com.afternote.domain.dailyquestion.model.UserDailyQuestion;
import com.afternote.domain.dailyquestion.repository.DailyQuestionRepository;
import com.afternote.domain.dailyquestion.repository.UserDailyQuestionRepository;
import com.afternote.domain.mindrecord.emotion.event.DailyQuestionEmotionAnalysisRequestedEvent;
import com.afternote.domain.receiver.dto.MindRecordReceiverSummaryResponse;
import com.afternote.domain.receiver.repository.UserDailyQuestionReceiverRepository;
import com.afternote.domain.receiver.service.MindRecordReceiverService;
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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DailyQuestionService {

    private final UserDailyQuestionRepository userDailyQuestionRepository;
    private final DailyQuestionRepository dailyQuestionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;
    private final MindRecordHtmlSanitizer mindRecordHtmlSanitizer;
    private final UserDailyQuestionReceiverRepository userDailyQuestionReceiverRepository;
    private final MindRecordReceiverService mindRecordReceiverService;

    @Transactional
    public DailyQuestionTodayResponse getTodayQuestion(Long userId) {
        LocalDate today = LocalDate.now();

        Optional<UserDailyQuestion> optionalTodayQuestion =
                userDailyQuestionRepository.findByUserIdAndQuestionDate(userId, today);

        UserDailyQuestion userDailyQuestion;

        if (optionalTodayQuestion.isPresent()) {
            userDailyQuestion = optionalTodayQuestion.get();
        } else {
            // 오늘 할당된 질문이 없으면 새로 가져와서 매핑
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

            long unansweredCount = dailyQuestionRepository.countUnansweredQuestions(userId);
            if (unansweredCount <= 0) {
                throw new CustomException(ErrorCode.DAILY_QUESTION_NOT_FOUND);
            }

            int randomOffset = ThreadLocalRandom.current().nextInt((int) unansweredCount);
            DailyQuestion nextQuestion = dailyQuestionRepository.findUnansweredQuestionByOffset(userId, randomOffset)
                    .orElseThrow(() -> new CustomException(ErrorCode.DAILY_QUESTION_NOT_FOUND));

            userDailyQuestion = UserDailyQuestion.builder()
                    .user(user)
                    .dailyQuestion(nextQuestion)
                    .questionDate(today)
                    .isAnswered(false)
                    .content("")
                    .isDraft(true)
                    .build();

            userDailyQuestionRepository.save(userDailyQuestion);
        }

        return DailyQuestionTodayResponse.builder()
                .questionId(userDailyQuestion.getId())
                .day(userDailyQuestion.getDailyQuestion().getId())
                .content(userDailyQuestion.getDailyQuestion().getContent())
                .isAnswered(userDailyQuestion.isAnswered())
                .isDraft(userDailyQuestion.isDraft())
                .receivers(mindRecordReceiverService.getUserDailyQuestionReceivers(userDailyQuestion.getId()))
                .build();
    }

    @Transactional
    public DailyQuestionAnswerResponse createAnswer(Long userId, DailyQuestionAnswerRequest request) {
        LocalDate today = LocalDate.now();
        UserDailyQuestion userDailyQuestion = userDailyQuestionRepository.findById(request.getQuestionId())
                .orElseThrow(() -> new CustomException(ErrorCode.DAILY_QUESTION_NOT_FOUND));

        if (!userDailyQuestion.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.NOT_ENOUGH_PERMISSION);
        }

        if (!today.equals(userDailyQuestion.getQuestionDate())) {
            throw new CustomException(ErrorCode.DAILY_QUESTION_DATE_MISMATCH);
        }

        if (userDailyQuestion.isAnswered() && !userDailyQuestion.isDraft()) {
            throw new CustomException(ErrorCode.DAILY_QUESTION_ALREADY_ANSWERED);
        }

        boolean isDraft = request.getIsDraft() != null ? request.getIsDraft() : false;
        userDailyQuestion.updateAnswer(
                mindRecordHtmlSanitizer.sanitize(request.getContent()),
                request.getImageUrl(),
                isDraft
        );
        if (!userDailyQuestion.isDraft()) {
            eventPublisher.publishEvent(new DailyQuestionEmotionAnalysisRequestedEvent(userId, userDailyQuestion.getId()));
        }

        List<MindRecordReceiverSummaryResponse> receivers = mindRecordReceiverService.replaceUserDailyQuestionReceivers(
                userId,
                userDailyQuestion,
                request.getReceiverIds(),
                !userDailyQuestion.isDraft()
        );

        return toAnswerResponse(userDailyQuestion, receivers);
    }

    @Transactional
    public DailyQuestionAnswerResponse updateAnswer(Long userId, Long userDailyQuestionId, DailyQuestionUpdateRequest request) {
        UserDailyQuestion userDailyQuestion = userDailyQuestionRepository.findById(userDailyQuestionId)
                .orElseThrow(() -> new CustomException(ErrorCode.DAILY_QUESTION_NOT_FOUND));

        if (!userDailyQuestion.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.NOT_ENOUGH_PERMISSION);
        }

        if (request.getContent() != null || request.getImageUrl() != null || request.getIsDraft() != null) {
            userDailyQuestion.updateAnswer(
                    request.getContent() != null
                            ? mindRecordHtmlSanitizer.sanitize(request.getContent())
                            : userDailyQuestion.getContent(),
                    request.getImageUrl() != null ? request.getImageUrl() : userDailyQuestion.getImageUrl(),
                    request.getIsDraft() != null ? request.getIsDraft() : userDailyQuestion.isDraft()
            );
            if (!userDailyQuestion.isDraft()) {
                eventPublisher.publishEvent(new DailyQuestionEmotionAnalysisRequestedEvent(userId, userDailyQuestion.getId()));
            }
        }

        List<MindRecordReceiverSummaryResponse> receivers;
        if (request.getReceiverIds() != null) {
            receivers = mindRecordReceiverService.replaceUserDailyQuestionReceivers(
                    userId,
                    userDailyQuestion,
                    request.getReceiverIds(),
                    !userDailyQuestion.isDraft()
            );
        } else {
            receivers = mindRecordReceiverService.getUserDailyQuestionReceivers(userDailyQuestion.getId());
        }

        return toAnswerResponse(userDailyQuestion, receivers);
    }

    @Transactional(readOnly = true)
    public List<DailyQuestionListResponse> getDailyQuestions(Long userId, LocalDate date, Boolean draftOnly) {
        List<UserDailyQuestion> questions;
        if (date != null) {
            questions = userDailyQuestionRepository.findByUserIdAndQuestionDateOrderByCreatedAtDesc(userId, date);
        } else {
            questions = userDailyQuestionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd E", Locale.KOREAN);

        List<Long> userDailyQuestionIds = questions.stream().map(UserDailyQuestion::getId).toList();
        Map<Long, List<MindRecordReceiverSummaryResponse>> receiversMap =
                mindRecordReceiverService.getUserDailyQuestionReceiversMap(userDailyQuestionIds);

        return questions.stream()
                .filter(q -> Boolean.TRUE.equals(draftOnly) ? q.isDraft() : !q.isDraft())
                .map(q -> DailyQuestionListResponse.builder()
                        .userDailyQuestionId(q.getId())
                        .title(q.getDailyQuestion().getContent())
                        .content(q.getContent())
                        .createdAt(q.getCreatedAt() != null ? q.getCreatedAt().format(formatter) : null)
                        .imageUrl(q.getImageUrl())
                        .isDraft(q.isDraft())
                        .receivers(receiversMap.getOrDefault(q.getId(), List.of()))
                        .build())
                .collect(Collectors.toList());
    }

    private DailyQuestionAnswerResponse toAnswerResponse(
            UserDailyQuestion userDailyQuestion,
            List<MindRecordReceiverSummaryResponse> receivers
    ) {
        return DailyQuestionAnswerResponse.builder()
                .userDailyQuestionId(userDailyQuestion.getId())
                .content(userDailyQuestion.getContent())
                .imageUrl(userDailyQuestion.getImageUrl())
                .isDraft(userDailyQuestion.isDraft())
                .receivers(receivers)
                .build();
    }

    @Transactional
    public void deleteAnswer(Long userId, Long userDailyQuestionId) {
        UserDailyQuestion userDailyQuestion = userDailyQuestionRepository.findById(userDailyQuestionId)
                .orElseThrow(() -> new CustomException(ErrorCode.DAILY_QUESTION_NOT_FOUND));

        if (!userDailyQuestion.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.NOT_ENOUGH_PERMISSION);
        }

        userDailyQuestionReceiverRepository.deleteByUserDailyQuestionId(userDailyQuestionId);
        userDailyQuestionRepository.delete(userDailyQuestion);
    }
}
