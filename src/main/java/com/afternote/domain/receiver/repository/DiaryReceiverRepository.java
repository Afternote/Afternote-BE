package com.afternote.domain.receiver.repository;

import com.afternote.domain.receiver.model.DiaryReceiver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM DiaryReceiver dr WHERE dr.diary.id = :diaryId")
    void deleteByDiaryId(@Param("diaryId") Long diaryId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM DiaryReceiver dr WHERE dr.diary.id IN :diaryIds")
    void deleteByDiaryIdIn(@Param("diaryIds") List<Long> diaryIds);

    @Query("""
        SELECT dr FROM DiaryReceiver dr
        JOIN FETCH dr.diary d
        WHERE dr.receiver.id = :receiverId
          AND d.isDraft = false
          AND (:start IS NULL OR d.entryDate >= :start)
          AND (:end IS NULL OR d.entryDate <= :end)
    """)
    List<DiaryReceiver> findReceivedDiaries(
            @Param("receiverId") Long receiverId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    boolean existsByReceiverId(Long receiverId);
}
