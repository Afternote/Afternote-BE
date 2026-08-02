package com.afternote.domain.user.service;

import com.afternote.domain.user.repository.WithdrawnUserRepository;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WithdrawalCooldownService {

    public static final int COOLDOWN_DAYS = 30;

    private final WithdrawnUserRepository withdrawnUserRepository;

    public void assertNotInCooldown(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        withdrawnUserRepository.findTopByEmailIgnoreCaseOrderByWithdrawnAtDesc(email.trim())
                .ifPresent(withdrawn -> {
                    LocalDateTime until = withdrawn.getWithdrawnAt().plusDays(COOLDOWN_DAYS);
                    if (LocalDateTime.now().isBefore(until)) {
                        throw new CustomException(ErrorCode.WITHDRAWAL_COOLDOWN);
                    }
                });
    }
}
