package com.afternote.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Builder
public record PasskeyCreationOptionsResponse(
        @Schema(description = "base64url challenge")
        String challenge,
        Rp rp,
        User user,
        List<PubKeyCredParam> pubKeyCredParams,
        Long timeout,
        String attestation,
        List<CredentialDescriptor> excludeCredentials,
        AuthenticatorSelection authenticatorSelection
) {
    public record Rp(String name, String id) {
    }

    public record User(String id, String name, String displayName) {
    }

    public record PubKeyCredParam(String type, int alg) {
    }

    public record CredentialDescriptor(String type, String id) {
    }

    public record AuthenticatorSelection(
            String residentKey,
            boolean requireResidentKey,
            String userVerification
    ) {
    }
}
