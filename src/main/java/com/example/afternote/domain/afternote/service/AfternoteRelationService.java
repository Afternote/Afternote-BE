package com.example.afternote.domain.afternote.service;

import com.example.afternote.domain.afternote.dto.AfternoteCreateRequest;
import com.example.afternote.domain.afternote.model.*;
import com.example.afternote.domain.afternote.service.relation.AfternoteCategoryRelationStrategy;
import com.example.afternote.domain.afternote.service.relation.AfternoteRelationStrategyFactory;
import com.example.afternote.domain.receiver.model.Receiver;
import com.example.afternote.domain.receiver.repository.ReceivedRepository;
import com.example.afternote.global.exception.CustomException;
import com.example.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Afternote 카테고리별 관계 데이터 저장을 담당하는 서비스
 */
@Service
@RequiredArgsConstructor
public class AfternoteRelationService {

    private final ReceivedRepository receiverRepository;
    private final AfternoteRelationStrategyFactory relationStrategyFactory;

    /**
     * 카테고리별 관계 데이터 저장
     */
    public void saveRelationsByCategory(Afternote afternote, AfternoteCreateRequest request) {
        // 모든 카테고리에서 receiver 저장 (옵션)
        if (request.getReceivers() != null && !request.getReceivers().isEmpty()) {
            saveReceivers(afternote, request);
        }

        AfternoteCategoryRelationStrategy strategy = relationStrategyFactory.get(request.getCategory());
        strategy.save(afternote, request);
    }

    /**
     * 카테고리별 관계 데이터 업데이트 (PATCH)
     */
    public void updateRelationsByCategory(
            Afternote afternote,
            AfternoteCreateRequest request,
            AfternoteCategoryType categoryType
    ) {
        // 모든 카테고리에서 receivers 업데이트 가능 (제공된 경우만)
        if (request.getReceivers() != null) {
            updateReceivers(afternote, request);
        }

        AfternoteCategoryRelationStrategy strategy = relationStrategyFactory.get(categoryType);
        strategy.update(afternote, request);
    }

    /**
     * Receivers 저장 (모든 카테고리에서 사용 가능)
     */
    private void saveReceivers(Afternote afternote, AfternoteCreateRequest request) {
        if (request.getReceivers() == null) return;

        for (AfternoteCreateRequest.ReceiverRequest receiverReq : request.getReceivers()) {
            Receiver receiver = receiverRepository.findById(receiverReq.getReceiverId())
                    .orElseThrow(() -> new CustomException(ErrorCode.RECEIVER_NOT_FOUND));

            afternote.getReceivers().add(createAfternoteReceiver(afternote, receiver));
        }
    }
    
    /**
     * Receivers 업데이트 (모든 카테고리에서 사용 가능, 제공된 receivers가 있으면 전체 교체)
     */
    public void updateReceivers(Afternote afternote, AfternoteCreateRequest request) {
        if (request.getReceivers() == null) return;
        
        // receivers가 제공된 경우에만 전체 교체
        afternote.getReceivers().clear();
        for (AfternoteCreateRequest.ReceiverRequest receiverReq : request.getReceivers()) {
            Receiver receiver = receiverRepository.findById(receiverReq.getReceiverId())
                    .orElseThrow(() -> new CustomException(ErrorCode.RECEIVER_NOT_FOUND));

            afternote.getReceivers().add(createAfternoteReceiver(afternote, receiver));
        }
    }
    
    // ========== Builder Helper Methods ==========

    private AfternoteReceiver createAfternoteReceiver(Afternote afternote, Receiver receiver) {
        return AfternoteReceiver.builder()
                .afternote(afternote)
                .receiver(receiver)
                .build();
    }

}
