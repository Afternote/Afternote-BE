package com.afternote.domain.deepthought.repository;

import com.afternote.domain.deepthought.model.DeepThought;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeepThoughtRepository extends JpaRepository<DeepThought, Long> {

    Optional<DeepThought> findByIdAndUserId(Long id, Long userId);

    @Query("SELECT DISTINCT dt FROM DeepThought dt " +
            "LEFT JOIN dt.tags t " +
            "WHERE dt.user.id = :userId " +
            "AND (:start IS NULL OR (dt.createdAt >= :start AND dt.createdAt < :end)) " +
            "AND (:category IS NULL OR dt.category = :category) " +
            "AND (:tag IS NULL OR t.title = :tag OR dt.title LIKE CONCAT('%', :tag, '%') OR dt.content LIKE CONCAT('%', :tag, '%')) " +
            "ORDER BY dt.createdAt DESC")
    List<DeepThought> search(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("tag") String tag,
            @Param("category") String category
    );

    long countByUserId(Long userId);

    @Query(value = "SELECT * FROM deep_thought dt WHERE dt.user_id = :userId LIMIT 1 OFFSET :offset", nativeQuery = true)
    Optional<DeepThought> findByUserIdWithOffset(@Param("userId") Long userId, @Param("offset") int offset);
}
