package com.afternote.domain.afternote.service.validation;

import com.afternote.domain.afternote.dto.AfternoteCreateRequest;
import com.afternote.domain.afternote.model.AfternoteCategoryType;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class GalleryValidationStrategy implements AfternoteCategoryValidationStrategy {

    @Override
    public AfternoteCategoryType category() {
        return AfternoteCategoryType.GALLERY;
    }

    @Override
    public void validateCreate(AfternoteCreateRequest request) {
        if (request.getCredentials() != null) {
            throw new CustomException(ErrorCode.INVALID_FIELD_FOR_GALLERY);
        }
        if (request.getPlaylist() != null) {
            throw new CustomException(ErrorCode.INVALID_FIELD_FOR_GALLERY);
        }

        // actions·receivers 는 선택 (없으면 빈 상태로 생성)
        AfternoteValidationCommons.validateOptionalReceivers(request);
    }

    @Override
    public void validateUpdate(AfternoteCreateRequest request) {
        if (request.getCredentials() != null) {
            throw new CustomException(ErrorCode.INVALID_FIELD_FOR_GALLERY);
        }
        if (request.getPlaylist() != null) {
            throw new CustomException(ErrorCode.INVALID_FIELD_FOR_GALLERY);
        }
        AfternoteValidationCommons.validateOptionalReceivers(request);
    }

}
