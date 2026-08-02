package com.afternote.domain.dailyquestion.repository;

import com.afternote.domain.dailyquestion.model.UserDailyQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UserDailyQuestionRepository extends JpaRepository<UserDailyQuestion, Long> {

    @Query("SELECT q.id FROM UserDailyQuestion q WHERE q.user.id = :userId")
    List<Long> findIdsByUserId(@Param("userId") Long userId);
    
    Optional<UserDailyQuestion> findByUserIdAndQuestionDate(Long userId, LocalDate questionDate);
    
    List<UserDailyQuestion> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    List<UserDailyQuestion> findByUserIdAndQuestionDateOrderByCreatedAtDesc(Long userId, LocalDate questionDate);

    List<UserDailyQuestion> findByUserIdAndQuestionDateBetweenOrderByQuestionDateAscCreatedAtAsc(
            Long userId,
            LocalDate fromInclusive,
            LocalDate toInclusive
    );
}