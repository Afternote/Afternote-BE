package com.afternote.global.observability;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionObservabilityConfigurationTest {

    @Test
    void exposesReadinessAndPrometheusOnDedicatedManagementPort() throws IOException {
        var propertySources = new YamlPropertySourceLoader().load(
                "application-prod",
                new ClassPathResource("application-prod.yml")
        );

        assertThat(propertySources).hasSize(1);
        var properties = propertySources.get(0);

        assertThat(properties.getProperty("management.server.port")).isEqualTo(8081);
        assertThat(properties.getProperty("management.endpoint.health.probes.enabled")).isEqualTo(true);
        assertThat(properties.getProperty("management.endpoint.health.group.readiness.include"))
                .isEqualTo("readinessState,db,redis");
        assertThat(properties.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health,prometheus");
        assertThat(properties.getProperty("management.health.mail.enabled")).isEqualTo(false);
    }
}
