package com.afternote.domain.afternote.service.validation;

import com.afternote.domain.afternote.dto.AfternoteCreateRequest;
import com.afternote.domain.afternote.model.AfternoteCategoryType;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
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

        // actions·receivers 는 선택 (없으면 빈 상태로 생성)
        // draft면 credentials 필수 검증 완화
        if (!request.isDraftValue()) {
            requireCredentials(request);
        }

        AfternoteValidationCommons.validateOptionalReceivers(request);
    }

    @Override
    public void validateUpdate(AfternoteCreateRequest request) {
        if (request.getPlaylist() != null) {
            throw new CustomException(ErrorCode.INVALID_FIELD_FOR_SOCIAL);
        }
        // receivers·credentials·title·actions·leaveMessage 는 수정 허용
        AfternoteValidationCommons.validateOptionalReceivers(request);
    }

    private void requireCredentials(AfternoteCreateRequest request) {
        if (request.getCredentials() == null) {
            throw new CustomException(ErrorCode.SOCIAL_CREDENTIALS_REQUIRED);
        }
        if (request.getCredentials().getId() == null || request.getCredentials().getId().isBlank()) {
            throw new CustomException(ErrorCode.SOCIAL_ACCOUNT_ID_REQUIRED);
        }
        if (request.getCredentials().getPassword() == null || request.getCredentials().getPassword().isBlank()) {
            throw new CustomException(ErrorCode.SOCIAL_ACCOUNT_PASSWORD_REQUIRED);
        }
    }

}
