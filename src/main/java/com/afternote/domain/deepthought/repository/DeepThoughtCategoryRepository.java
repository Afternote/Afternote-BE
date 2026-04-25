package com.afternote.domain.deepthought.repository;

import com.afternote.domain.deepthought.model.DeepThoughtCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeepThoughtCategoryRepository extends JpaRepository<DeepThoughtCategory, Long> {

    Optional<DeepThoughtCategory> findByIdAndUserId(Long id, Long userId);

    List<DeepThoughtCategory> findByUserIdOrderByIdAsc(Long userId);

    boolean existsByUserIdAndTitle(Long userId, String title);
}
