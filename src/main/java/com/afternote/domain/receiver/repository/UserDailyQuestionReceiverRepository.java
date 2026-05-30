package com.afternote.domain.receiver.repository;

import com.afternote.domain.receiver.model.UserDailyQuestionReceiver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserDailyQuestionReceiverRepository extends JpaRepository<UserDailyQuestionReceiver, Long> {

    List<UserDailyQuestionReceiver> findByUserDailyQuestionIdAndReceiverIdIn(Long userDailyQuestionId, List<Long> receiverIds);

    @Query("""
        SELECT udqr FROM UserDailyQuestionReceiver udqr
        JOIN FETCH udqr.receiver
        WHERE udqr.userDailyQuestion.id IN :userDailyQuestionIds
    """)
    List<UserDailyQuestionReceiver> findByUserDailyQuestionIdIn(
            @Param("userDailyQuestionIds") List<Long> userDailyQuestionIds
    );

    void deleteByUserDailyQuestionId(Long userDailyQuestionId);

    @Query("""
        SELECT udqr FROM UserDailyQuestionReceiver udqr
        JOIN FETCH udqr.userDailyQuestion udq
        JOIN FETCH udq.dailyQuestion dq
        WHERE udqr.receiver.id = :receiverId
          AND udq.isDraft = false
          AND udq.isAnswered = true
          AND (:start IS NULL OR udq.createdAt >= :start)
          AND (:end IS NULL OR udq.createdAt < :end)
    """)
    List<UserDailyQuestionReceiver> findReceivedDailyQuestions(
            @Param("receiverId") Long receiverId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    boolean existsByReceiverId(Long receiverId);
}
