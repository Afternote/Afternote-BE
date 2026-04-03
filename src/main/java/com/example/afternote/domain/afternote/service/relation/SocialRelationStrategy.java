package com.example.afternote.domain.afternote.service.relation;

import com.example.afternote.domain.afternote.dto.AfternoteCreateRequest;
import com.example.afternote.domain.afternote.model.Afternote;
import com.example.afternote.domain.afternote.model.AfternoteCategoryType;
import com.example.afternote.domain.afternote.model.AfternoteSecureContent;
import com.example.afternote.global.util.ChaChaEncryptionUtil;
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
            afternote.getSecureContents().add(createSecureContent(afternote, "account_id", encryptedId));
        }

        if (request.getCredentials().getPassword() != null) {
            String encryptedPassword = chaChaEncryptionUtil.encrypt(request.getCredentials().getPassword());
            afternote.getSecureContents().add(createSecureContent(afternote, "account_password", encryptedPassword));
        }
    }

    @Override
    public void update(Afternote afternote, AfternoteCreateRequest request) {
        if (request.getCredentials() == null) return;

        if (request.getCredentials().getId() != null) {
            String encryptedId = chaChaEncryptionUtil.encrypt(request.getCredentials().getId());
            afternote.getSecureContents().removeIf(sc -> "account_id".equals(sc.getKeyName()));
            afternote.getSecureContents().add(createSecureContent(afternote, "account_id", encryptedId));
        }

        if (request.getCredentials().getPassword() != null) {
            String encryptedPassword = chaChaEncryptionUtil.encrypt(request.getCredentials().getPassword());
            afternote.getSecureContents().removeIf(sc -> "account_password".equals(sc.getKeyName()));
            afternote.getSecureContents().add(createSecureContent(afternote, "account_password", encryptedPassword));
        }
    }

    private AfternoteSecureContent createSecureContent(Afternote afternote, String keyName, String value) {
        return AfternoteSecureContent.builder()
                .afternote(afternote)
                .keyName(keyName)
                .encryptedValue(value)
                .build();
    }
}
