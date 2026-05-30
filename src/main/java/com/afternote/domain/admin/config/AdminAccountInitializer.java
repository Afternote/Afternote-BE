package com.afternote.domain.admin.config;

import com.afternote.domain.user.model.AuthProvider;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.model.UserRole;
import com.afternote.domain.user.model.UserStatus;
import com.afternote.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_EMAIL:}")
    private String adminEmail;

    @Value("${ADMIN_PASSWORD:}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            log.info("ADMIN_EMAIL 또는 ADMIN_PASSWORD 환경 변수가 없어 Admin 계정 초기화를 건너뜁니다.");
            return;
        }

        userRepository.findByEmail(adminEmail).ifPresentOrElse(
                existingUser -> {
                    if (existingUser.getRole() != UserRole.ADMIN) {
                        existingUser.updateRole(UserRole.ADMIN);
                        log.info("Existing user {} role updated to ADMIN.", adminEmail);
                    } else {
                        log.info("Admin account already exists: {}", adminEmail);
                    }
                },
                () -> {
                    User admin = User.builder()
                            .email(adminEmail)
                            .password(passwordEncoder.encode(adminPassword))
                            .name("Admin")
                            .status(UserStatus.ACTIVE)
                            .provider(AuthProvider.LOCAL)
                            .build();
                    // Need to set role to ADMIN - add a method to User for this
                    admin.updateRole(UserRole.ADMIN);
                    userRepository.save(admin);
                    log.info("Admin account created: {}", adminEmail);
                }
        );
    }
}
