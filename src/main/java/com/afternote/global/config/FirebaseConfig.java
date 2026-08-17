package com.afternote.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Configuration
@EnableConfigurationProperties(FirebaseProperties.class)
public class FirebaseConfig {

    @Bean
    @ConditionalOnExpression("!'${firebase.service-account-json:}'.isBlank()")
    FirebaseApp firebaseApp(FirebaseProperties properties) throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        GoogleCredentials credentials = GoogleCredentials.fromStream(
                new ByteArrayInputStream(properties.getServiceAccountJson().getBytes(StandardCharsets.UTF_8))
        ).createScoped(List.of("https://www.googleapis.com/auth/firebase.messaging"));

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .setProjectId(properties.getProjectId())
                .build();

        FirebaseApp app = FirebaseApp.initializeApp(options);
        log.info("Firebase Admin initialized. projectId={}", properties.getProjectId());
        return app;
    }

    @Bean
    @ConditionalOnExpression("!'${firebase.service-account-json:}'.isBlank()")
    FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}
