package com.afternote.domain.receiver.repository;

import com.afternote.domain.receiver.model.TimeLetterReceiver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TimeLetterReceiverRepository extends JpaRepository<TimeLetterReceiver, Long> {

    @Query("""
            SELECT DISTINCT tlr FROM TimeLetterReceiver tlr
            JOIN FETCH tlr.timeLetter tl
            JOIN FETCH tl.user
            LEFT JOIN FETCH tl.blocks
            WHERE tlr.receiver.id = :receiverId
            ORDER BY tlr.createdAt DESC
            """)
    List<TimeLetterReceiver> findByReceiverIdWithTimeLetter(@Param("receiverId") Long receiverId);

    @Query("""
            SELECT DISTINCT tlr FROM TimeLetterReceiver tlr
            JOIN FETCH tlr.timeLetter tl
            JOIN FETCH tl.user
            LEFT JOIN FETCH tl.blocks
            WHERE tlr.id = :id AND tlr.receiver.id = :receiverId
            """)
    Optional<TimeLetterReceiver> findByIdAndReceiverIdWithTimeLetter(
            @Param("id") Long id,
            @Param("receiverId") Long receiverId
    );

    List<TimeLetterReceiver> findByTimeLetterId(Long timeLetterId);

    List<TimeLetterReceiver> findByTimeLetterIdIn(List<Long> timeLetterIds);

    boolean existsByTimeLetterId(Long timeLetterId);

    void deleteByTimeLetterId(Long timeLetterId);

    void deleteByTimeLetterIdIn(List<Long> timeLetterIds);
}