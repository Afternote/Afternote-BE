package com.example.afternote.domain.afternote.service.validation;

import com.example.afternote.domain.afternote.dto.AfternoteCreateRequest;
import com.example.afternote.domain.afternote.model.AfternoteCategoryType;
import com.example.afternote.global.exception.CustomException;
import com.example.afternote.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class SocialValidationStrategy implements AfternoteCategoryValidationStrategy {

    @Override
    public AfternoteCategoryType category() {
        return AfternoteCategoryType.SOCIAL;
    }

    @Override
    public void validateCreate(AfternoteCreateRequest request) {
        if (request.getPlaylist() != null) {
            throw new CustomException(ErrorCode.INVALID_FIELD_FOR_SOCIAL);
        }

        if (request.getActions() == null || request.getActions().isEmpty()) {
            throw new CustomException(ErrorCode.ACTIONS_REQUIRED);
        }
        if (request.getCredentials() == null) {
            throw new CustomException(ErrorCode.SOCIAL_CREDENTIALS_REQUIRED);
        }
        if (request.getCredentials().getId() == null || request.getCredentials().getId().isBlank()) {
            throw new CustomException(ErrorCode.SOCIAL_ACCOUNT_ID_REQUIRED);
        }
        if (request.getCredentials().getPassword() == null || request.getCredentials().getPassword().isBlank()) {
            throw new CustomException(ErrorCode.SOCIAL_ACCOUNT_PASSWORD_REQUIRED);
        }

        AfternoteValidationCommons.validateRequiredReceivers(request);
    }

    @Override
    public void validateUpdate(AfternoteCreateRequest request) {
        if (request.getReceivers() != null && !request.getReceivers().isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_FIELD_FOR_SOCIAL);
        }
        if (request.getPlaylist() != null) {
            throw new CustomException(ErrorCode.INVALID_FIELD_FOR_SOCIAL);
        }
    }

}
