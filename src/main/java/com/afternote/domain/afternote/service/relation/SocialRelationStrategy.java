package com.afternote.domain.afternote.service.relation;

import com.afternote.domain.afternote.dto.AfternoteCreateRequest;
import com.afternote.domain.afternote.model.Afternote;
import com.afternote.domain.afternote.model.AfternoteCategoryType;
import com.afternote.domain.afternote.model.AfternoteSecureContent;
import com.afternote.global.util.ChaChaEncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SocialRelationStrategy implements AfternoteCategoryRelationStrategy {

    private final ChaChaEncryptionUtil chaChaEncryptionUtil;

    @Override
    public AfternoteCategoryType category() {
        return AfternoteCategoryType.SOCIAL;
    }

    @Override
    public void save(Afternote afternote, AfternoteCreateRequest request) {
        if (request.getCredentials() == null) return;

        if (request.getCredentials().getId() != null) {
            String encryptedId = chaChaEncryptionUtil.encrypt(request.getCredentials().getId());
            afternote.getSecureContents().add(createSecureContent(afternote, EncryptedKey.ACCOUNT_ID, encryptedId));
        }

        if (request.getCredentials().getPassword() != null) {
            String encryptedPassword = chaChaEncryptionUtil.encrypt(request.getCredentials().getPassword());
            afternote.getSecureContents().add(createSecureContent(afternote, EncryptedKey.ACCOUNT_PASSWORD, encryptedPassword));
        }
    }

    @Override
    public void update(Afternote afternote, AfternoteCreateRequest request) {
        if (request.getCredentials() == null) return;

        if (request.getCredentials().getId() != null) {
            String encryptedId = chaChaEncryptionUtil.encrypt(request.getCredentials().getId());
            afternote.getSecureContents().removeIf(sc -> EncryptedKey.ACCOUNT_ID.matches(sc.getKeyName()));
            afternote.getSecureContents().add(createSecureContent(afternote, EncryptedKey.ACCOUNT_ID, encryptedId));
        }

        if (request.getCredentials().getPassword() != null) {
            String encryptedPassword = chaChaEncryptionUtil.encrypt(request.getCredentials().getPassword());
            afternote.getSecureContents().removeIf(sc -> EncryptedKey.ACCOUNT_PASSWORD.matches(sc.getKeyName()));
            afternote.getSecureContents().add(createSecureContent(afternote, EncryptedKey.ACCOUNT_PASSWORD, encryptedPassword));
        }
    }

    private AfternoteSecureContent createSecureContent(Afternote afternote, EncryptedKey keyName, String value) {
        return AfternoteSecureContent.builder()
                .afternote(afternote)
                .keyName(keyName.value())
                .encryptedValue(value)
                .build();
    }
}
