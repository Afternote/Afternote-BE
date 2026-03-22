package com.example.afternote.domain.afternote.service.validation;

import com.example.afternote.domain.afternote.dto.AfternoteCreateRequest;
import com.example.afternote.domain.afternote.model.AfternoteCategoryType;

public interface AfternoteCategoryValidationStrategy {

    AfternoteCategoryType category();

    void validateCreate(AfternoteCreateRequest request);

    void validateUpdate(AfternoteCreateRequest request);
}
