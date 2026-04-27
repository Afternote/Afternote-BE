package com.afternote.domain.dailyquestion.repository;

import com.afternote.domain.dailyquestion.model.DailyQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DailyQuestionRepository extends JpaRepository<DailyQuestion, Long> {

    @Query(value = "SELECT COUNT(*) FROM daily_question dq " +
            "WHERE NOT EXISTS (SELECT 1 FROM user_daily_question udq " +
            "WHERE udq.user_id = :userId AND udq.question_id = dq.id)", nativeQuery = true)
    long countUnansweredQuestions(@Param("userId") Long userId);

    @Query(value = "SELECT dq.* FROM daily_question dq " +
            "WHERE NOT EXISTS (SELECT 1 FROM user_daily_question udq " +
            "WHERE udq.user_id = :userId AND udq.question_id = dq.id) " +
            "LIMIT 1 OFFSET :offset", nativeQuery = true)
    Optional<DailyQuestion> findUnansweredQuestionByOffset(@Param("userId") Long userId, @Param("offset") int offset);
}