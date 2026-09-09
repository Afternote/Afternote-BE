package com.afternote.domain.auth.service;

import com.afternote.domain.auth.dto.LoginResponse;
import com.afternote.domain.auth.dto.PasskeyCreationOptionsResponse;
import com.afternote.domain.auth.dto.PasskeyRequestOptionsResponse;
import com.afternote.domain.auth.dto.PasskeyResponse;
import com.afternote.domain.auth.model.UserPasskey;
import com.afternote.domain.auth.repository.UserPasskeyRepository;
import com.afternote.domain.user.model.AuthProvider;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.model.UserStatus;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.global.config.PasskeyProperties;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.converter.AttestedCredentialDataConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.credential.CredentialRecordImpl;
import com.webauthn4j.data.AuthenticationData;
import com.webauthn4j.data.AuthenticationParameters;
import com.webauthn4j.data.PublicKeyCredentialParameters;
import com.webauthn4j.data.PublicKeyCredentialType;
import com.webauthn4j.data.RegistrationData;
import com.webauthn4j.data.RegistrationParameters;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.Challenge;
import com.webauthn4j.data.extension.authenticator.AuthenticationExtensionsAuthenticatorOutputs;
import com.webauthn4j.server.ServerProperty;
import com.webauthn4j.util.Base64UrlUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PasskeyService {

    private static final long OPTIONS_TIMEOUT_MS = 300_000L;
    private static final String DEFAULT_DISPLAY_NAME = "패스키";

    private final UserRepository userRepository;
    private final UserPasskeyRepository userPasskeyRepository;
    private final PasskeyChallengeService passkeyChallengeService;
    private final PasskeyProperties passkeyProperties;
    private final WebAuthnManager webAuthnManager;
    private final ObjectConverter webAuthnObjectConverter;
    private final ObjectMapper objectMapper;
    private final AuthService authService;

    public PasskeyCreationOptionsResponse registerOptions(Long userId) {
        User user = findUser(userId);
        Challenge challenge = passkeyChallengeService.issue(PasskeyChallengeService.TYPE_REGISTER, userId);

        List<PasskeyCreationOptionsResponse.CredentialDescriptor> exclude = userPasskeyRepository
                .findAllByUserOrderByIdDesc(user)
                .stream()
                .map(pk -> new PasskeyCreationOptionsResponse.CredentialDescriptor(
                        "public-key",
                        Base64UrlUtil.encodeToString(pk.getCredentialId())
                ))
                .toList();

        String userHandle = Base64UrlUtil.encodeToString(userHandleBytes(user.getId()));
        return PasskeyCreationOptionsResponse.builder()
                .challenge(PasskeyChallengeService.encode(challenge))
                .rp(new PasskeyCreationOptionsResponse.Rp(
                        passkeyProperties.getRpName(),
                        passkeyProperties.getRpId()
                ))
                .user(new PasskeyCreationOptionsResponse.User(userHandle, user.getEmail(), user.getName()))
                .pubKeyCredParams(List.of(
                        new PasskeyCreationOptionsResponse.PubKeyCredParam("public-key", -7),
                        new PasskeyCreationOptionsResponse.PubKeyCredParam("public-key", -257)
                ))
                .timeout(OPTIONS_TIMEOUT_MS)
                .attestation("none")
                .excludeCredentials(exclude)
                .authenticatorSelection(new PasskeyCreationOptionsResponse.AuthenticatorSelection(
                        "required",
                        true,
                        "required"
                ))
                .build();
    }

    @Transactional
    public PasskeyResponse register(Long userId, JsonNode body) {
        User user = findUser(userId);
        String credentialJson = credentialJson(body);
        RegistrationData parsed;
        try {
            parsed = webAuthnManager.parseRegistrationResponseJSON(credentialJson);
        } catch (CustomException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("passkey register parse failed: {}", e.toString());
            throw new CustomException(ErrorCode.PASSKEY_VERIFICATION_FAILED);
        }
        if (parsed == null
                || parsed.getCollectedClientData() == null
                || parsed.getCollectedClientData().getChallenge() == null) {
            throw new CustomException(ErrorCode.PASSKEY_CHALLENGE_INVALID);
        }
        Long challengeUserId = passkeyChallengeService.consume(
                parsed.getCollectedClientData().getChallenge(),
                PasskeyChallengeService.TYPE_REGISTER
        );
        if (!userId.equals(challengeUserId)) {
            throw new CustomException(ErrorCode.PASSKEY_CHALLENGE_INVALID);
        }

        ServerProperty serverProperty = serverProperty(parsed.getCollectedClientData().getChallenge());
        try {
            webAuthnManager.verify(parsed, new RegistrationParameters(
                    serverProperty,
                    pubKeyCredParams(),
                    true,
                    true
            ));
        } catch (CustomException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("passkey register verify failed: {}", e.toString());
            throw new CustomException(ErrorCode.PASSKEY_VERIFICATION_FAILED);
        }

        if (parsed.getAttestationObject() == null
                || parsed.getAttestationObject().getAuthenticatorData() == null) {
            throw new CustomException(ErrorCode.PASSKEY_VERIFICATION_FAILED);
        }
        AttestedCredentialData attested = parsed.getAttestationObject()
                .getAuthenticatorData()
                .getAttestedCredentialData();
        if (attested == null) {
            throw new CustomException(ErrorCode.PASSKEY_VERIFICATION_FAILED);
        }
        byte[] credentialId = attested.getCredentialId();
        if (userPasskeyRepository.existsByCredentialId(credentialId)) {
            throw new CustomException(ErrorCode.PASSKEY_CREDENTIAL_ALREADY_REGISTERED);
        }

        var authenticatorData = parsed.getAttestationObject().getAuthenticatorData();
        AttestedCredentialDataConverter converter = new AttestedCredentialDataConverter(webAuthnObjectConverter);
        UserPasskey saved;
        try {
            saved = userPasskeyRepository.save(UserPasskey.builder()
                    .user(user)
                    .credentialId(credentialId)
                    .attestedCredentialData(converter.convert(attested))
                    .signCount(authenticatorData.getSignCount())
                    .uvInitialized(authenticatorData.isFlagUV())
                    .backupEligible(authenticatorData.isFlagBE())
                    .backupState(authenticatorData.isFlagBS())
                    .displayName(displayName(body))
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.PASSKEY_CREDENTIAL_ALREADY_REGISTERED);
        }
        return toResponse(saved);
    }

    public PasskeyRequestOptionsResponse authenticateOptions() {
        Challenge challenge = passkeyChallengeService.issue(PasskeyChallengeService.TYPE_AUTH, null);
        return PasskeyRequestOptionsResponse.builder()
                .challenge(PasskeyChallengeService.encode(challenge))
                .timeout(OPTIONS_TIMEOUT_MS)
                .rpId(passkeyProperties.getRpId())
                .allowCredentials(List.of())
                .userVerification("required")
                .build();
    }

    @Transactional
    public LoginResponse authenticate(JsonNode body) {
        String credentialJson = credentialJson(body);
        AuthenticationData parsed;
        try {
            parsed = webAuthnManager.parseAuthenticationResponseJSON(credentialJson);
        } catch (CustomException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("passkey authenticate parse failed: {}", e.toString());
            throw new CustomException(ErrorCode.PASSKEY_VERIFICATION_FAILED);
        }
        if (parsed == null
                || parsed.getCollectedClientData() == null
                || parsed.getCollectedClientData().getChallenge() == null) {
            throw new CustomException(ErrorCode.PASSKEY_CHALLENGE_INVALID);
        }
        passkeyChallengeService.consume(
                parsed.getCollectedClientData().getChallenge(),
                PasskeyChallengeService.TYPE_AUTH
        );

        byte[] credentialId = parsed.getCredentialId();
        if (credentialId == null) {
            throw new CustomException(ErrorCode.PASSKEY_CREDENTIAL_NOT_FOUND);
        }
        UserPasskey passkey = userPasskeyRepository.findByCredentialId(credentialId)
                .orElseThrow(() -> new CustomException(ErrorCode.PASSKEY_CREDENTIAL_NOT_FOUND));
        if (passkey.getUser().getStatus() != UserStatus.ACTIVE) {
            throw new CustomException(ErrorCode.PASSKEY_CREDENTIAL_NOT_FOUND);
        }

        AttestedCredentialDataConverter converter = new AttestedCredentialDataConverter(webAuthnObjectConverter);
        AttestedCredentialData attested = converter.convert(passkey.getAttestedCredentialData());
        CredentialRecordImpl record = new CredentialRecordImpl(
                null,
                passkey.getUvInitialized(),
                passkey.getBackupEligible(),
                passkey.getBackupState(),
                passkey.getSignCount(),
                attested,
                new AuthenticationExtensionsAuthenticatorOutputs<>(),
                null,
                null,
                null
        );

        ServerProperty serverProperty = serverProperty(parsed.getCollectedClientData().getChallenge());
        try {
            AuthenticationData verified = webAuthnManager.verify(parsed, new AuthenticationParameters(
                    serverProperty,
                    record,
                    null,
                    true,
                    true
            ));
            if (verified.getAuthenticatorData() == null) {
                throw new CustomException(ErrorCode.PASSKEY_VERIFICATION_FAILED);
            }
            passkey.updateSignCount(verified.getAuthenticatorData().getSignCount());
        } catch (CustomException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("passkey authenticate verify failed: {}", e.toString());
            throw new CustomException(ErrorCode.PASSKEY_VERIFICATION_FAILED);
        }

        return authService.issueTokens(passkey.getUser());
    }

    public List<PasskeyResponse> list(Long userId) {
        User user = findUser(userId);
        return userPasskeyRepository.findAllByUserOrderByIdDesc(user).stream()
                .map(PasskeyService::toResponse)
                .toList();
    }

    @Transactional
    public void delete(Long userId, Long passkeyId) {
        User user = userRepository.findWithProvidersById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
        UserPasskey passkey = userPasskeyRepository.findById(passkeyId)
                .orElseThrow(() -> new CustomException(ErrorCode.PASSKEY_NOT_FOUND));
        if (!passkey.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.PASSKEY_NOT_FOUND);
        }
        assertNotLastLoginMeans(user);
        userPasskeyRepository.delete(passkey);
    }

    @Transactional
    public void deleteAllForUser(Long userId) {
        userRepository.findById(userId).ifPresent(userPasskeyRepository::deleteAllByUser);
    }

    private void assertNotLastLoginMeans(User user) {
        boolean hasPassword = user.getPassword() != null && !user.getPassword().isBlank();
        boolean hasSocial = user.getProviders().stream().anyMatch(p -> p != AuthProvider.LOCAL);
        if (!hasPassword && !hasSocial && userPasskeyRepository.countByUser(user) <= 1) {
            throw new CustomException(ErrorCode.CANNOT_UNLINK_LAST_CREDENTIAL);
        }
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
    }

    private ServerProperty serverProperty(Challenge challenge) {
        Set<Origin> origins = new LinkedHashSet<>();
        for (String originUrl : passkeyProperties.trustedOriginUrls()) {
            Origin parsed = parseOrigin(originUrl);
            if (parsed != null) {
                origins.add(parsed);
            }
        }
        if (origins.isEmpty()) {
            Origin fallback = parseOrigin(passkeyProperties.getOrigin());
            if (fallback != null) {
                origins.add(fallback);
            }
        }
        return new ServerProperty(origins, passkeyProperties.getRpId(), challenge);
    }

    private static Origin parseOrigin(String originUrl) {
        if (originUrl == null || originUrl.isBlank()) {
            return null;
        }
        try {
            return new Origin(originUrl);
        } catch (RuntimeException e) {
            log.warn("passkey skip invalid origin: {}", originUrl);
            return null;
        }
    }

    private List<PublicKeyCredentialParameters> pubKeyCredParams() {
        List<PublicKeyCredentialParameters> params = new ArrayList<>();
        params.add(new PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.ES256));
        params.add(new PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.RS256));
        return params;
    }

    private String credentialJson(JsonNode body) {
        if (body == null || body.isNull()) {
            throw new CustomException(ErrorCode.PASSKEY_VERIFICATION_FAILED);
        }
        JsonNode credential = body.has("credential") ? body.get("credential") : body;
        try {
            return objectMapper.writeValueAsString(credential);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.PASSKEY_VERIFICATION_FAILED);
        }
    }

    private String displayName(JsonNode body) {
        if (body != null && body.hasNonNull("displayName")) {
            String name = body.get("displayName").asText("").trim();
            if (!name.isEmpty()) {
                return name.length() > 80 ? name.substring(0, 80) : name;
            }
        }
        return DEFAULT_DISPLAY_NAME;
    }

    private static byte[] userHandleBytes(Long userId) {
        return ByteBuffer.allocate(8).putLong(userId).array();
    }

    private static PasskeyResponse toResponse(UserPasskey passkey) {
        return PasskeyResponse.builder()
                .id(passkey.getId())
                .displayName(passkey.getDisplayName())
                .createdAt(passkey.getCreatedAt())
                .build();
    }
}
