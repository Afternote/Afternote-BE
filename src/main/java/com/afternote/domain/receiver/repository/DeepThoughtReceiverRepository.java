package com.afternote.domain.receiver.repository;

import com.afternote.domain.receiver.model.DeepThoughtReceiver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeepThoughtReceiverRepository extends JpaRepository<DeepThoughtReceiver, Long> {

    List<DeepThoughtReceiver> findByDeepThoughtIdAndReceiverIdIn(Long deepThoughtId, List<Long> receiverIds);

    void deleteByDeepThoughtId(Long deepThoughtId);
}
