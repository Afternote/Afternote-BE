package com.afternote.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.receiver-invitation")
public class ReceiverInvitationProperties {

    private String baseUrl = "https://afternote.kro.kr/invitations";
    private long expirationDays = 7;

    public String normalizedBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Receiver invitation base URL must not be blank");
        }
        return baseUrl.replaceAll("/+$", "");
    }
}
