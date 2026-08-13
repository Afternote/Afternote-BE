package com.afternote.domain.afternote.service;

import com.afternote.domain.afternote.dto.AfternoteCreateRequest;
import com.afternote.domain.afternote.model.Afternote;
import com.afternote.domain.afternote.model.AfternoteCategoryType;
import com.afternote.domain.afternote.service.relation.EncryptedKey;
import com.afternote.domain.afternote.service.validation.AfternoteCategoryValidationStrategy;
import com.afternote.domain.afternote.service.validation.AfternoteValidationCommons;
import com.afternote.domain.afternote.service.validation.AfternoteValidationStrategyFactory;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Afternote 카테고리별 필드 검증
 */
@Component
@RequiredArgsConstructor
public class AfternoteValidator {

    private final AfternoteValidationStrategyFactory validationStrategyFactory;


    /**
     * POST 요청 검증
     * - 있어야 하는 필드: 무조건 있어야 함
     * - 없어야 하는 필드: 무조건 null
     */
    public void validateCreateRequest(AfternoteCreateRequest request) {
        if (request.getCategory() == null) {
            throw new CustomException(ErrorCode.CATEGORY_REQUIRED);
        }

        AfternoteValidationCommons.validateLeaveMessage(request.getLeaveMessage());
        AfternoteCategoryValidationStrategy strategy = validationStrategyFactory.get(request.getCategory());
        strategy.validateCreate(request);
    }

    /**
     * PATCH 요청 검증
     * - 없어야 하는 필드: 있으면 안됨
     * - 있어야 하는 필드: 있든 없든 상관없음
     */
    public void validateUpdateRequest(AfternoteCreateRequest request, AfternoteCategoryType category) {
        // 카테고리 변경 불가
        if (request.getCategory() != null && request.getCategory() != category) {
            throw new CustomException(ErrorCode.CATEGORY_CANNOT_BE_CHANGED);
        }

        AfternoteValidationCommons.validateLeaveMessage(request.getLeaveMessage());
        AfternoteCategoryValidationStrategy strategy = validationStrategyFactory.get(category);
        strategy.validateUpdate(request);
    }

    /**
     * 정식 등록(비-draft) 상태로 남을 때 credentials/playlist 등 필수값 검증.
     * PATCH는 부분 갱신이므로 request + 기존 엔티티를 함께 본다.
     */
    public void validatePublishRequirements(AfternoteCreateRequest request, Afternote afternote) {
        boolean willBeDraft = request.getIsDraft() != null
                ? Boolean.TRUE.equals(request.getIsDraft())
                : Boolean.TRUE.equals(afternote.getIsDraft());
        if (willBeDraft) {
            return;
        }

        switch (afternote.getCategoryType()) {
            case SOCIAL, BUSINESS -> validateCredentialsPublish(request, afternote);
            case PLAYLIST -> validatePlaylistPublish(request, afternote);
            case GALLERY -> {
                // 추가 필수 없음
            }
        }
    }

    private void validateCredentialsPublish(AfternoteCreateRequest request, Afternote afternote) {
        boolean hasId = hasNonBlank(request.getCredentials() != null ? request.getCredentials().getId() : null)
                || hasSecureKey(afternote, EncryptedKey.ACCOUNT_ID);
        boolean hasPassword = hasNonBlank(request.getCredentials() != null ? request.getCredentials().getPassword() : null)
                || hasSecureKey(afternote, EncryptedKey.ACCOUNT_PASSWORD);

        if (!hasId || !hasPassword) {
            if (!hasId && !hasPassword) {
                throw new CustomException(ErrorCode.SOCIAL_CREDENTIALS_REQUIRED);
            }
            if (!hasId) {
                throw new CustomException(ErrorCode.SOCIAL_ACCOUNT_ID_REQUIRED);
            }
            throw new CustomException(ErrorCode.SOCIAL_ACCOUNT_PASSWORD_REQUIRED);
        }
    }

    private void validatePlaylistPublish(AfternoteCreateRequest request, Afternote afternote) {
        if (request.getPlaylist() != null) {
            if (request.getPlaylist().getSongs() == null || request.getPlaylist().getSongs().isEmpty()) {
                throw new CustomException(ErrorCode.PLAYLIST_SONGS_REQUIRED);
            }
            return;
        }
        if (afternote.getPlaylist() == null
                || afternote.getPlaylist().getItems() == null
                || afternote.getPlaylist().getItems().isEmpty()) {
            throw new CustomException(ErrorCode.PLAYLIST_REQUIRED);
        }
    }

    private boolean hasSecureKey(Afternote afternote, EncryptedKey key) {
        return afternote.getSecureContents() != null
                && afternote.getSecureContents().stream()
                .anyMatch(sc -> key.matches(sc.getKeyName()));
    }

    private boolean hasNonBlank(String value) {
        return value != null && !value.isBlank();
    }
}
