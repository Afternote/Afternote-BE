package com.afternote.domain.deepthought.repository;

import com.afternote.domain.deepthought.dto.DeepThoughtTagCountResponse;
import com.afternote.domain.deepthought.model.DeepThought;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
            "LEFT JOIN dt.category c " +
            "WHERE dt.user.id = :userId " +
            "AND (:start IS NULL OR (dt.createdAt >= :start AND dt.createdAt < :end)) " +
            "AND (:category IS NULL OR c.title = :category) " +
            "AND (:tag IS NULL OR t.title = :tag OR dt.title LIKE CONCAT('%', :tag, '%') OR dt.content LIKE CONCAT('%', :tag, '%')) " +
            "AND (:draftOnly IS NULL OR :draftOnly = false OR dt.isDraft = true) " +
            "ORDER BY dt.createdAt DESC")
    List<DeepThought> search(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("tag") String tag,
            @Param("category") String category,
            @Param("draftOnly") Boolean draftOnly
    );

    @Query("SELECT NEW com.afternote.domain.deepthought.dto.DeepThoughtTagCountResponse(t.title, COUNT(DISTINCT dt.id)) "
            + "FROM DeepThought dt JOIN dt.tags t "
            + "LEFT JOIN dt.category c "
            + "WHERE dt.user.id = :userId "
            + "AND (:start IS NULL OR (dt.createdAt >= :start AND dt.createdAt < :end)) "
            + "AND (:category IS NULL OR c.title = :category) "
            + "AND (:draftOnly IS NULL OR :draftOnly = false OR dt.isDraft = true) "
            + "GROUP BY t.title "
            + "ORDER BY COUNT(DISTINCT dt.id) DESC, t.title ASC")
    List<DeepThoughtTagCountResponse> aggregateTagCounts(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("category") String category,
            @Param("draftOnly") Boolean draftOnly
    );

    long countByUserId(Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE DeepThought dt SET dt.category = null WHERE dt.user.id = :userId AND dt.category.id = :categoryId")
    int clearCategoryByUserIdAndCategoryId(@Param("userId") Long userId, @Param("categoryId") Long categoryId);

    @Query(value = "SELECT * FROM deep_thought dt WHERE dt.user_id = :userId LIMIT 1 OFFSET :offset", nativeQuery = true)
    Optional<DeepThought> findByUserIdWithOffset(@Param("userId") Long userId, @Param("offset") int offset);

    List<DeepThought> findByUserIdAndIsDraftFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
            Long userId,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive
    );
}
