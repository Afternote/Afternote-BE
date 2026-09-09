package com.afternote.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@ConfigurationProperties(prefix = "passkey")
public class PasskeyProperties {

    private static final String ANDROID_APK_KEY_HASH_PREFIX = "android:apk-key-hash:";

    private String rpId = "afternote.kro.kr";
    private String rpName = "AfterNote";
    private String origin = "https://afternote.kro.kr";
    private long challengeTtlSeconds = 300;
    private String androidPackageName = "com.afternote.afternote_fe";
    /** 쉼표 구분 SHA-256. 비어 있으면 assetlinks fingerprints 빈 배열. */
    private String androidSha256 = "";

    public List<String> androidSha256Fingerprints() {
        if (androidSha256 == null || androidSha256.isBlank()) {
            return List.of();
        }
        return Arrays.stream(androidSha256.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * WebAuthn 검증에 쓰는 신뢰 origin.
     * 웹 origin 에 더해, assetlinks 와 같은 SHA-256 지문에서 Android {@code apk-key-hash} 를 파생한다.
     */
    public Set<String> trustedOriginUrls() {
        LinkedHashSet<String> origins = new LinkedHashSet<>();
        if (origin != null && !origin.isBlank()) {
            origins.add(origin.trim());
        }
        for (String fingerprint : androidSha256Fingerprints()) {
            String apkOrigin = apkKeyHashOrigin(fingerprint);
            if (apkOrigin != null) {
                origins.add(apkOrigin);
            }
        }
        return Set.copyOf(origins);
    }

    static String apkKeyHashOrigin(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return null;
        }
        String hex = fingerprint.replace(":", "").replace(" ", "");
        if (hex.isEmpty() || (hex.length() % 2) != 0) {
            return null;
        }
        byte[] digest;
        try {
            digest = HexFormat.of().parseHex(hex);
        } catch (IllegalArgumentException e) {
            return null;
        }
        String hash = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        return ANDROID_APK_KEY_HASH_PREFIX + hash;
    }
}
