package com.example.afternote.domain.afternote.service.validation;

import com.example.afternote.domain.afternote.dto.AfternoteCreateRequest;
import com.example.afternote.domain.afternote.model.AfternoteCategoryType;
import com.example.afternote.global.exception.CustomException;
import com.example.afternote.global.exception.ErrorCode;
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

        if (request.getActions() == null || request.getActions().isEmpty()) {
            throw new CustomException(ErrorCode.ACTIONS_REQUIRED);
        }

        validateRequiredReceivers(request);
    }

    @Override
    public void validateUpdate(AfternoteCreateRequest request) {
        if (request.getCredentials() != null) {
            throw new CustomException(ErrorCode.INVALID_FIELD_FOR_GALLERY);
        }
        if (request.getPlaylist() != null) {
            throw new CustomException(ErrorCode.INVALID_FIELD_FOR_GALLERY);
        }
    }

    private void validateRequiredReceivers(AfternoteCreateRequest request) {
        if (request.getReceivers() == null || request.getReceivers().isEmpty()) {
            throw new CustomException(ErrorCode.RECEIVERS_REQUIRED);
        }
        for (AfternoteCreateRequest.ReceiverRequest receiver : request.getReceivers()) {
            if (receiver.getReceiverId() == null) {
                throw new CustomException(ErrorCode.GALLERY_RECEIVER_ID_REQUIRED);
            }
        }
    }
}
