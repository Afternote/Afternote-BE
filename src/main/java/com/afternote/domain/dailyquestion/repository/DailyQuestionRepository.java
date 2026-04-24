package com.afternote.domain.dailyquestion.repository;

import com.afternote.domain.dailyquestion.model.DailyQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DailyQuestionRepository extends JpaRepository<DailyQuestion, Long> {

    @Query("""
    select q from DailyQuestion q
    where q.isActive = true
      and not exists (
          select 1
          from UserDailyQuestion udq
          where udq.dailyQuestion = q
            and udq.user.id = :userId
      )
    order by function('RAND')
""")
    List<DailyQuestion> findRandomUnexposedQuestion(@Param("userId") Long userId);

    @Query("""
    select q from DailyQuestion q
    where q.isActive = true
      and exists (
          select 1
          from UserDailyQuestion udq
          where udq.dailyQuestion = q
            and udq.user.id = :userId
      )
    order by function('RAND')
""")
    List<DailyQuestion> findRandomExposedQuestion(@Param("userId") Long userId);
}
