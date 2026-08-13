package com.afternote.domain.afternote.service.relation;

import com.afternote.domain.afternote.dto.AfternoteCreateRequest;
import com.afternote.domain.afternote.model.Afternote;
import com.afternote.domain.afternote.model.AfternoteCategoryType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BusinessRelationStrategy implements AfternoteCategoryRelationStrategy {

    private final CredentialsRelationSupport credentialsRelationSupport;

    @Override
    public AfternoteCategoryType category() {
        return AfternoteCategoryType.BUSINESS;
    }

    @Override
    public void save(Afternote afternote, AfternoteCreateRequest request) {
        credentialsRelationSupport.save(afternote, request);
    }

    @Override
    public void update(Afternote afternote, AfternoteCreateRequest request) {
        credentialsRelationSupport.update(afternote, request);
    }
}
