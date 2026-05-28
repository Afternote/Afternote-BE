package com.afternote.domain.receiver.repository;

import com.afternote.domain.deepthought.dto.DeepThoughtTagCountResponse;
import com.afternote.domain.receiver.model.DeepThoughtReceiver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DeepThoughtReceiverRepository extends JpaRepository<DeepThoughtReceiver, Long> {

    List<DeepThoughtReceiver> findByDeepThoughtIdAndReceiverIdIn(Long deepThoughtId, List<Long> receiverIds);

    void deleteByDeepThoughtId(Long deepThoughtId);

    @Query("""
        SELECT DISTINCT dtr FROM DeepThoughtReceiver dtr
        JOIN FETCH dtr.deepThought dt
        LEFT JOIN FETCH dt.category c
        LEFT JOIN FETCH dt.tags tags
        WHERE dtr.receiver.id = :receiverId
          AND dt.isDraft = false
          AND (:start IS NULL OR dt.createdAt >= :start)
          AND (:end IS NULL OR dt.createdAt < :end)
          AND (:category IS NULL OR c.title = :category)
          AND (
                :tag IS NULL
                OR tags.title = :tag
                OR dt.title LIKE CONCAT('%', :tag, '%')
                OR dt.content LIKE CONCAT('%', :tag, '%')
          )
    """)
    List<DeepThoughtReceiver> findReceivedDeepThoughts(
            @Param("receiverId") Long receiverId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("category") String category,
            @Param("tag") String tag
    );

    @Query("""
        SELECT DISTINCT c.title FROM DeepThoughtReceiver dtr
        JOIN dtr.deepThought dt
        LEFT JOIN dt.category c
        WHERE dtr.receiver.id = :receiverId
          AND dt.isDraft = false
          AND c.title IS NOT NULL
          AND (:start IS NULL OR dt.createdAt >= :start)
          AND (:end IS NULL OR dt.createdAt < :end)
        ORDER BY c.title ASC
    """)
    List<String> findReceivedCategoryTitles(
            @Param("receiverId") Long receiverId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
        SELECT NEW com.afternote.domain.deepthought.dto.DeepThoughtTagCountResponse(
            t.title,
            COUNT(DISTINCT dt.id)
        )
        FROM DeepThoughtReceiver dtr
        JOIN dtr.deepThought dt
        JOIN dt.tags t
        LEFT JOIN dt.category c
        WHERE dtr.receiver.id = :receiverId
          AND dt.isDraft = false
          AND (:start IS NULL OR dt.createdAt >= :start)
          AND (:end IS NULL OR dt.createdAt < :end)
          AND (:category IS NULL OR c.title = :category)
        GROUP BY t.title
        ORDER BY COUNT(DISTINCT dt.id) DESC, t.title ASC
    """)
    List<DeepThoughtTagCountResponse> aggregateReceivedTagCounts(
            @Param("receiverId") Long receiverId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("category") String category
    );

    boolean existsByReceiverId(Long receiverId);
}
