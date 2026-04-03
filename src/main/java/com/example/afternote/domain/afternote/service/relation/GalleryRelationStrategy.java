package com.example.afternote.domain.afternote.service.relation;

import com.example.afternote.domain.afternote.dto.AfternoteCreateRequest;
import com.example.afternote.domain.afternote.model.Afternote;
import com.example.afternote.domain.afternote.model.AfternoteCategoryType;
import org.springframework.stereotype.Component;

@Component
public class GalleryRelationStrategy implements AfternoteCategoryRelationStrategy {

    @Override
    public AfternoteCategoryType category() {
        return AfternoteCategoryType.GALLERY;
    }

    @Override
    public void save(Afternote afternote, AfternoteCreateRequest request) {
        // receivers는 AfternoteRelationService에서 공통 처리
    }

    @Override
    public void update(Afternote afternote, AfternoteCreateRequest request) {
        // GALLERY 카테고리 전용 관계 업데이트 없음
    }
}
