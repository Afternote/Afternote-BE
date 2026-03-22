package com.afternote.domain.afternote.service.validation;

import com.afternote.domain.afternote.dto.AfternoteCreateRequest;
import com.afternote.domain.afternote.model.AfternoteCategoryType;

public interface AfternoteCategoryValidationStrategy {

    AfternoteCategoryType category();

    void validateCreate(AfternoteCreateRequest request);

    void validateUpdate(AfternoteCreateRequest request);
}
