package com.afternote.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "passkey")
public class PasskeyProperties {

    private String rpId = "afternote.kro.kr";
    private String rpName = "AfterNote";
    private String origin = "https://afternote.kro.kr";
    private long challengeTtlSeconds = 300;
    private String androidPackageName = "com.afternote.app";
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
}
