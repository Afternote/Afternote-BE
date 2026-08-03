package com.afternote.domain.delivery.repository;

import com.afternote.domain.delivery.model.ConditionState;
import com.afternote.domain.delivery.model.DeliveryCondition;
import com.afternote.domain.delivery.model.DeliveryContentType;
import com.afternote.domain.user.model.DeliveryConditionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeliveryConditionRepository extends JpaRepository<DeliveryCondition, Long> {

    Optional<DeliveryCondition> findByReceiverIdAndContentType(Long receiverId, DeliveryContentType contentType);

    List<DeliveryCondition> findByReceiverId(Long receiverId);

    List<DeliveryCondition> findByUserIdAndReceiverIdAndConditionType(
            Long userId, Long receiverId, DeliveryConditionType conditionType);

    List<DeliveryCondition> findByConditionTypeAndState(
            DeliveryConditionType conditionType, ConditionState state);

    boolean existsByReceiverIdAndState(Long receiverId, ConditionState state);

    void deleteByUserId(Long userId);

    void deleteByReceiverId(Long receiverId);
}
