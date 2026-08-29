package com.afternote.domain.dailyquestion.repository;

import com.afternote.domain.dailyquestion.model.UserDailyQuestion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UserDailyQuestionRepository extends JpaRepository<UserDailyQuestion, Long> {

    @Query("SELECT q.id FROM UserDailyQuestion q WHERE q.user.id = :userId")
    List<Long> findIdsByUserId(@Param("userId") Long userId);
    
    Optional<UserDailyQuestion> findByIdAndUser_Id(Long id, Long userId);

    Optional<UserDailyQuestion> findByUserIdAndQuestionDate(Long userId, LocalDate questionDate);
    
    List<UserDailyQuestion> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    List<UserDailyQuestion> findByUserIdAndQuestionDateOrderByCreatedAtDesc(Long userId, LocalDate questionDate);

    List<UserDailyQuestion> findByUserIdAndQuestionDateBetweenOrderByQuestionDateAscCreatedAtAsc(
            Long userId,
            LocalDate fromInclusive,
            LocalDate toInclusive
    );

    @Query("SELECT DISTINCT q.user.id FROM UserDailyQuestion q "
            + "WHERE q.isDraft = false AND q.isAnswered = true "
            + "AND q.questionDate >= :fromInclusive AND q.questionDate <= :toInclusive")
    List<Long> findUserIdsWithFinalAnswersInQuestionDateRange(
            @Param("fromInclusive") LocalDate fromInclusive,
            @Param("toInclusive") LocalDate toInclusive
    );

    /** 감정 행이 없는 최종 답변 (userId, userDailyQuestionId). 백필용. */
    @Query(value = """
            SELECT q.user_id, q.id
            FROM user_daily_question q
            LEFT JOIN emotions e
              ON e.user_id = q.user_id AND e.source_type = 'DAILY_QUESTION' AND e.source_id = q.id
            WHERE q.is_draft = 0 AND q.is_answered = 1 AND e.id IS NULL
            ORDER BY q.id ASC
            """, nativeQuery = true)
    List<Object[]> findFinalAnswersMissingEmotion(Pageable pageable);
}