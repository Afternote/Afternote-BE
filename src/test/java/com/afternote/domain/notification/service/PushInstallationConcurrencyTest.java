package com.afternote.domain.notification.service;

import com.afternote.domain.notification.dto.*;
import com.afternote.domain.notification.model.*;
import com.afternote.domain.notification.repository.*;
import com.afternote.domain.user.model.*;
import com.afternote.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

class PushInstallationConcurrencyTest {
    @Test void permissionReportAndTokenBindingShareOneInstallation() throws Exception {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var users = context.getBean(UserRepository.class);
            var service = context.getBean(UserPushTokenService.class);
            var installations = context.getBean(PushInstallationRepository.class);
            User user = users.save(User.builder().email("concurrent@example.com").password("pw")
                    .name("tester").status(UserStatus.ACTIVE).provider(AuthProvider.LOCAL).build());
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                for (int iteration = 0; iteration < 8; iteration++) {
                    UUID installationId = UUID.randomUUID();
                    String token = "token-" + installationId;
                    CyclicBarrier start = new CyclicBarrier(2);
                    Future<?> report = pool.submit(() -> {
                        await(start);
                        return service.reportPermission(user.getId(),
                                new PushInstallationRequest(installationId, NotificationPermission.DENIED));
                    });
                    Future<?> register = pool.submit(() -> {
                        await(start);
                        return service.registerOrRefresh(user.getId(),
                                new RegisterPushTokenRequest(token, PushPlatform.ANDROID, null, installationId));
                    });
                    report.get(10, TimeUnit.SECONDS);
                    register.get(10, TimeUnit.SECONDS);
                    assertThat(installations.findByUser_IdAndInstallationId(user.getId(), installationId.toString()))
                            .hasValueSatisfying(value -> assertThat(value.getPermission()).isEqualTo(NotificationPermission.DENIED));
                }
                assertThat(installations.count()).isEqualTo(8);
            } finally {
                pool.shutdownNow();
            }
        }
    }

    private static void await(CyclicBarrier barrier) {
        try { barrier.await(5, TimeUnit.SECONDS); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaAuditing
    @EnableJpaRepositories(basePackageClasses = {UserRepository.class, UserPushTokenRepository.class})
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:push-concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setPackagesToScan("com.afternote.domain");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            Properties properties = new Properties();
            properties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
            factory.setJpaProperties(properties);
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
        @Bean UserPushTokenService userPushTokenService(PushInstallationRepository installations,
                UserRepository users, UserPushTokenRepository tokens) {
            return new UserPushTokenService(installations, users, tokens);
        }
    }
}
