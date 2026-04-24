package com.afternote.domain.mindrecord.repository;

import com.afternote.domain.mindrecord.question.model.UserDailyQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserDailyQuestionRepository
        extends JpaRepository<UserDailyQuestion, Long> {

        List<UserDailyQuestion> findByUserIdOrderByCreatedAtDesc(Long userId);
}