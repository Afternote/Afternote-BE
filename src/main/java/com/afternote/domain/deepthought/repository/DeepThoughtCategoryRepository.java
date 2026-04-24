package com.afternote.domain.deepthought.repository;

import com.afternote.domain.deepthought.model.DeepThoughtCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeepThoughtCategoryRepository extends JpaRepository<DeepThoughtCategory, Long> {
}
