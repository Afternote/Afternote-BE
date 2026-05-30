package com.afternote.domain.receiver.repository;

import com.afternote.domain.receiver.model.DiaryReceiver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DiaryReceiverRepository extends JpaRepository<DiaryReceiver, Long> {

    List<DiaryReceiver> findByDiaryIdAndReceiverIdIn(Long diaryId, List<Long> receiverIds);

    @Query("""
        SELECT dr FROM DiaryReceiver dr
        JOIN FETCH dr.receiver
        WHERE dr.diary.id IN :diaryIds
    """)
    List<DiaryReceiver> findByDiaryIdIn(@Param("diaryIds") List<Long> diaryIds);

    void deleteByDiaryId(Long diaryId);

    @Query("""
        SELECT dr FROM DiaryReceiver dr
        JOIN FETCH dr.diary d
        WHERE dr.receiver.id = :receiverId
          AND d.isDraft = false
          AND (:start IS NULL OR d.createdAt >= :start)
          AND (:end IS NULL OR d.createdAt < :end)
    """)
    List<DiaryReceiver> findReceivedDiaries(
            @Param("receiverId") Long receiverId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    boolean existsByReceiverId(Long receiverId);
}
