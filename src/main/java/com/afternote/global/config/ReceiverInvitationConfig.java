package com.afternote.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableConfigurationProperties(ReceiverInvitationProperties.class)
public class ReceiverInvitationConfig {

    @Bean
    Clock receiverInvitationClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
