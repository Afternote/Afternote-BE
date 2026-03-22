package com.example.afternote.domain.afternote.service.relation;

import com.example.afternote.domain.afternote.dto.AfternoteCreateRequest;
import com.example.afternote.domain.afternote.model.Afternote;
import com.example.afternote.domain.afternote.model.AfternoteCategoryType;

public interface AfternoteCategoryRelationStrategy {

    AfternoteCategoryType category();

    void save(Afternote afternote, AfternoteCreateRequest request);

    void update(Afternote afternote, AfternoteCreateRequest request);
}
