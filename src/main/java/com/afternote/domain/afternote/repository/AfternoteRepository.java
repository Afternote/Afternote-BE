package com.afternote.domain.afternote.repository;

import com.afternote.domain.afternote.model.Afternote;
import com.afternote.domain.afternote.model.AfternoteCategoryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AfternoteRepository extends JpaRepository<Afternote, Long> {

    @Query("SELECT a.id FROM Afternote a WHERE a.user.id = :userId")
    List<Long> findIdsByUserId(@Param("userId") Long userId);
    
    // 전체 목록 페이징 조회 (isDraft 필터)
    @Query("SELECT a FROM Afternote a WHERE a.user.id = :userId AND a.isDraft = :isDraft ORDER BY a.createdAt DESC")
    Page<Afternote> findByUserIdAndIsDraftOrderByCreatedAtDesc(
            @Param("userId") Long userId,
            @Param("isDraft") Boolean isDraft,
            Pageable pageable);
    
    // 카테고리별 필터링 페이징 조회 (isDraft 필터)
    @Query("SELECT a FROM Afternote a WHERE a.user.id = :userId AND a.categoryType = :categoryType AND a.isDraft = :isDraft ORDER BY a.createdAt DESC")
    Page<Afternote> findByUserIdAndCategoryTypeAndIsDraftOrderByCreatedAtDesc(
            @Param("userId") Long userId,
            @Param("categoryType") AfternoteCategoryType categoryType,
            @Param("isDraft") Boolean isDraft,
            Pageable pageable);
    
    // 해당 사용자의 최대 sortOrder 조회
    @Query("SELECT MAX(a.sortOrder) FROM Afternote a WHERE a.user.id = :userId")
    Optional<Integer> findMaxSortOrderByUserId(@Param("userId") Long userId);
}
