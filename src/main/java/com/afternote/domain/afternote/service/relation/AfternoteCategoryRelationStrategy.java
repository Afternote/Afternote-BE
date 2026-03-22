package com.afternote.domain.afternote.service.relation;

import com.afternote.domain.afternote.dto.AfternoteCreateRequest;
import com.afternote.domain.afternote.model.Afternote;
import com.afternote.domain.afternote.model.AfternoteCategoryType;

public interface AfternoteCategoryRelationStrategy {

    AfternoteCategoryType category();

    void save(Afternote afternote, AfternoteCreateRequest request);

    void update(Afternote afternote, AfternoteCreateRequest request);
}
