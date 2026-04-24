package com.afternote.domain.mindrecord.thought.repository;

import com.afternote.domain.mindrecord.thought.model.DeepThoughtCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeepThoughtCategoryRepository extends JpaRepository<DeepThoughtCategory, Long> {
}