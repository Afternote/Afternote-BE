package com.afternote.domain.user.service;

import com.afternote.domain.user.model.WithdrawnUser;
import com.afternote.domain.user.repository.WithdrawnUserRepository;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class WithdrawalCooldownServiceTest {

    @InjectMocks
    private WithdrawalCooldownService withdrawalCooldownService;

    @Mock
    private WithdrawnUserRepository withdrawnUserRepository;

    @Test
    @DisplayName("탈퇴 30일 이내면 WITHDRAWAL_COOLDOWN")
    void withinCooldown_Fail() {
        WithdrawnUser withdrawn = WithdrawnUser.of("a@test.com", 1L);
        ReflectionTestUtils.setField(withdrawn, "withdrawnAt", LocalDateTime.now().minusDays(1));
        given(withdrawnUserRepository.findTopByEmailIgnoreCaseOrderByWithdrawnAtDesc("a@test.com"))
                .willReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> withdrawalCooldownService.assertNotInCooldown("a@test.com"))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.WITHDRAWAL_COOLDOWN));
    }

    @Test
    @DisplayName("탈퇴 30일 지나면 통과")
    void afterCooldown_Ok() {
        WithdrawnUser withdrawn = WithdrawnUser.of("a@test.com", 1L);
        ReflectionTestUtils.setField(withdrawn, "withdrawnAt", LocalDateTime.now().minusDays(31));
        given(withdrawnUserRepository.findTopByEmailIgnoreCaseOrderByWithdrawnAtDesc("a@test.com"))
                .willReturn(Optional.of(withdrawn));

        assertThatCode(() -> withdrawalCooldownService.assertNotInCooldown("a@test.com"))
                .doesNotThrowAnyException();
    }
}
