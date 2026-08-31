package com.afternote.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "firebase")
public class FirebaseProperties {

    private String projectId = "afternote-android";

    private String androidPackageName = "com.afternote.afternote_fe";

    private String serviceAccountJson = "";

    public boolean isConfigured() {
        return serviceAccountJson != null && !serviceAccountJson.isBlank();
    }
}
