package com.afternote.domain.dailyquestion.repository;

import com.afternote.domain.dailyquestion.model.UserDailyQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UserDailyQuestionRepository extends JpaRepository<UserDailyQuestion, Long> {
    
    Optional<UserDailyQuestion> findByUserIdAndQuestionDate(Long userId, LocalDate questionDate);
    
    List<UserDailyQuestion> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    List<UserDailyQuestion> findByUserIdAndQuestionDateOrderByCreatedAtDesc(Long userId, LocalDate questionDate);
}