package com.afternote.domain.dailyquestion.service;

import com.afternote.domain.dailyquestion.dto.*;
import com.afternote.domain.dailyquestion.model.DailyQuestion;
import com.afternote.domain.dailyquestion.model.UserDailyQuestion;
import com.afternote.domain.dailyquestion.repository.DailyQuestionRepository;
import com.afternote.domain.dailyquestion.repository.UserDailyQuestionRepository;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DailyQuestionService {

    private final UserDailyQuestionRepository userDailyQuestionRepository;
    private final DailyQuestionRepository dailyQuestionRepository;
    private final UserRepository userRepository;

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
                .content(userDailyQuestion.getDailyQuestion().getContent())
                .isAnswered(userDailyQuestion.isAnswered())
                .build();
    }

    @Transactional
    public DailyQuestionAnswerResponse createAnswer(Long userId, DailyQuestionAnswerRequest request) {
        UserDailyQuestion userDailyQuestion = userDailyQuestionRepository.findById(request.getQuestionId())
                .orElseThrow(() -> new CustomException(ErrorCode.DAILY_QUESTION_NOT_FOUND));

        if (!userDailyQuestion.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.NOT_ENOUGH_PERMISSION);
        }

        userDailyQuestion.updateAnswer(
                request.getContent(),
                request.getImageUrl(),
                request.getIsDraft() != null ? request.getIsDraft() : false
        );

        return DailyQuestionAnswerResponse.builder()
                .userDailyQuestionId(userDailyQuestion.getId())
                .content(userDailyQuestion.getContent())
                .imageUrl(userDailyQuestion.getImageUrl())
                .isDraft(userDailyQuestion.isDraft())
                .build();
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
                    request.getContent() != null ? request.getContent() : userDailyQuestion.getContent(),
                    request.getImageUrl() != null ? request.getImageUrl() : userDailyQuestion.getImageUrl(),
                    request.getIsDraft() != null ? request.getIsDraft() : userDailyQuestion.isDraft()
            );
        }

        return DailyQuestionAnswerResponse.builder()
                .userDailyQuestionId(userDailyQuestion.getId())
                .content(userDailyQuestion.getContent())
                .imageUrl(userDailyQuestion.getImageUrl())
                .isDraft(userDailyQuestion.isDraft())
                .build();
    }

    @Transactional(readOnly = true)
    public List<DailyQuestionListResponse> getDailyQuestions(Long userId, LocalDate date) {
        List<UserDailyQuestion> questions;
        if (date != null) {
            questions = userDailyQuestionRepository.findByUserIdAndQuestionDateOrderByCreatedAtDesc(userId, date);
        } else {
            questions = userDailyQuestionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd E", Locale.KOREAN);

        return questions.stream()
                .filter(UserDailyQuestion::isAnswered)
                .map(q -> DailyQuestionListResponse.builder()
                        .userDailyQuestionId(q.getId())
                        .title(q.getDailyQuestion().getContent())
                        .content(q.getContent())
                        .createdAt(q.getCreatedAt() != null ? q.getCreatedAt().format(formatter) : null)
                        .imageUrl(q.getImageUrl())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteAnswer(Long userId, Long userDailyQuestionId) {
        UserDailyQuestion userDailyQuestion = userDailyQuestionRepository.findById(userDailyQuestionId)
                .orElseThrow(() -> new CustomException(ErrorCode.DAILY_QUESTION_NOT_FOUND));

        if (!userDailyQuestion.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.NOT_ENOUGH_PERMISSION);
        }

        userDailyQuestionRepository.delete(userDailyQuestion);
    }
}