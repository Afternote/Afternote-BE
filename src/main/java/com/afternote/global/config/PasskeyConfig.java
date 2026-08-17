package com.afternote.global.config;

import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.converter.util.ObjectConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PasskeyProperties.class)
public class PasskeyConfig {

    @Bean
    ObjectConverter webAuthnObjectConverter() {
        return new ObjectConverter();
    }

    @Bean
    WebAuthnManager webAuthnManager(ObjectConverter webAuthnObjectConverter) {
        return WebAuthnManager.createNonStrictWebAuthnManager(webAuthnObjectConverter);
    }
}
