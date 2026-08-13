package com.afternote.domain.afternote.service.validation;

import com.afternote.domain.afternote.dto.AfternoteCreateRequest;
import com.afternote.domain.afternote.model.AfternoteCategoryType;
import org.springframework.stereotype.Component;

@Component
public class BusinessValidationStrategy implements AfternoteCategoryValidationStrategy {

    @Override
    public AfternoteCategoryType category() {
        return AfternoteCategoryType.BUSINESS;
    }

    @Override
    public void validateCreate(AfternoteCreateRequest request) {
        CredentialsCategoryValidation.validateCreate(request);
    }

    @Override
    public void validateUpdate(AfternoteCreateRequest request) {
        CredentialsCategoryValidation.validateUpdate(request);
    }
}
