package com.afternote.domain.deepthought.repository;

import com.afternote.domain.deepthought.model.DeepThought;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeepThoughtRepository extends JpaRepository<DeepThought, Long> {
}
