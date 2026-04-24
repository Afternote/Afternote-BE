package com.afternote.domain.mindrecord.thought.repository;

import com.afternote.domain.mindrecord.thought.model.DeepThought;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeepThoughtRepository extends JpaRepository<DeepThought, Long> {
}