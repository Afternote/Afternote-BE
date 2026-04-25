package com.afternote.domain.receiver.repository;

import com.afternote.domain.receiver.model.DeepThoughtReceiver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeepThoughtReceiverRepository extends JpaRepository<DeepThoughtReceiver, Long> {
}