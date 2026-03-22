package com.example.afternote.domain.afternote.service;

import com.example.afternote.domain.afternote.dto.AfternoteCreateRequest;
import com.example.afternote.domain.afternote.model.AfternoteCategoryType;
import com.example.afternote.domain.afternote.service.validation.AfternoteCategoryValidationStrategy;
import com.example.afternote.domain.afternote.service.validation.AfternoteValidationStrategyFactory;
import com.example.afternote.global.exception.CustomException;
import com.example.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Afternote 카테고리별 필드 검증
 */
@Component
@RequiredArgsConstructor
public class AfternoteValidator {

    private final AfternoteValidationStrategyFactory validationStrategyFactory;


    /**
     * POST 요청 검증
     * - 있어야 하는 필드: 무조건 있어야 함
     * - 없어야 하는 필드: 무조건 null
     */
    public void validateCreateRequest(AfternoteCreateRequest request) {
        if (request.getCategory() == null) {
            throw new CustomException(ErrorCode.CATEGORY_REQUIRED);
        }

        AfternoteCategoryValidationStrategy strategy = validationStrategyFactory.get(request.getCategory());
        strategy.validateCreate(request);
    }

    /**
     * PATCH 요청 검증
     * - 없어야 하는 필드: 있으면 안됨
     * - 있어야 하는 필드: 있든 없든 상관없음
     */
    public void validateUpdateRequest(AfternoteCreateRequest request, AfternoteCategoryType category) {
        // 카테고리 변경 불가
        if (request.getCategory() != null && request.getCategory() != category) {
            throw new CustomException(ErrorCode.CATEGORY_CANNOT_BE_CHANGED);
        }

        AfternoteCategoryValidationStrategy strategy = validationStrategyFactory.get(category);
        strategy.validateUpdate(request);
    }
}
