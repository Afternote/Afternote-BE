package com.afternote.domain.receiver.service;

import com.afternote.domain.receiver.model.Receiver;
import com.afternote.domain.receiver.model.ReceiverInvitation;
import com.afternote.domain.receiver.model.UserReceiver;
import com.afternote.domain.receiver.repository.ReceiverInvitationRepository;
import com.afternote.domain.receiver.repository.ReceiverRepository;
import com.afternote.domain.receiver.repository.UserReceiverRepository;
import com.afternote.domain.user.model.AuthProvider;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.model.UserStatus;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.global.config.ReceiverInvitationProperties;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReceiverInvitationServiceTest {

    @InjectMocks
    private ReceiverInvitationService receiverInvitationService;

    @Mock private ReceiverInvitationRepository receiverInvitationRepository;
    @Mock private ReceiverRepository receiverRepository;
    @Mock private UserReceiverRepository userReceiverRepository;
    @Mock private UserRepository userRepository;
    @Mock private ReceiverInvitationProperties properties;
    @Mock private Clock clock;

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-09-06T06:00:00Z");

    @BeforeEach
    void setUp() {
        given(clock.instant()).willReturn(NOW);
        given(clock.getZone()).willReturn(SEOUL);
    }

    @Test
    @DisplayName("초대 생성 시 원문 토큰이 포함된 URL을 반환하고 DB에는 해시만 저장한다")
    void createInvitation_success() {
        User inviter = user(1L, "sender@test.com", "발신자", null);
        given(userRepository.findById(1L)).willReturn(Optional.of(inviter));
        given(properties.getExpirationDays()).willReturn(7L);
        given(properties.normalizedBaseUrl()).willReturn("https://afternote.kro.kr/invitations");
        given(receiverInvitationRepository.save(any())).willAnswer(invocation -> {
            ReceiverInvitation invitation = invocation.getArgument(0);
            ReflectionTestUtils.setField(invitation, "id", 10L);
            return invitation;
        });

        var response = receiverInvitationService.createInvitation(1L);

        assertThat(response.invitationId()).isEqualTo(10L);
        assertThat(response.invitationUrl()).startsWith("https://afternote.kro.kr/invitations/");
        String rawToken = response.invitationUrl().substring(response.invitationUrl().lastIndexOf('/') + 1);
        assertThat(response.invitationToken()).isEqualTo(rawToken);
        ArgumentCaptor<ReceiverInvitation> captor = ArgumentCaptor.forClass(ReceiverInvitation.class);
        verify(receiverInvitationRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).hasSize(64).doesNotContain(rawToken);
    }

    @Test
    @DisplayName("초대 수락 시 회원 정보로 관계와 메시지가 없는 수신자를 등록한다")
    void acceptInvitation_success() {
        User inviter = user(1L, "sender@test.com", "발신자", null);
        User invitee = user(2L, "receiver@test.com", "수신자", "010-1234-5678");
        ReceiverInvitation invitation = invitation(1L, now().plusDays(1));
        given(receiverInvitationRepository.findByTokenHashForUpdate(any())).willReturn(Optional.of(invitation));
        given(userRepository.findById(2L)).willReturn(Optional.of(invitee));
        given(userRepository.findById(1L)).willReturn(Optional.of(inviter));
        given(receiverRepository.existsByUserIdAndAcceptedUserId(1L, 2L)).willReturn(false);
        given(receiverRepository.saveAndFlush(any())).willAnswer(invocation -> {
            Receiver receiver = invocation.getArgument(0);
            ReflectionTestUtils.setField(receiver, "id", 20L);
            return receiver;
        });

        var response = receiverInvitationService.acceptInvitation(2L, "raw-token");

        assertThat(response.receiverId()).isEqualTo(20L);
        assertThat(response.inviterName()).isEqualTo("발신자");

        ArgumentCaptor<Receiver> receiverCaptor = ArgumentCaptor.forClass(Receiver.class);
        verify(receiverRepository).saveAndFlush(receiverCaptor.capture());
        Receiver receiver = receiverCaptor.getValue();
        assertThat(receiver.getUserId()).isEqualTo(1L);
        assertThat(receiver.getAcceptedUserId()).isEqualTo(2L);
        assertThat(receiver.getName()).isEqualTo("수신자");
        assertThat(receiver.getEmail()).isEqualTo("receiver@test.com");
        assertThat(receiver.getPhone()).isEqualTo("010-1234-5678");
        assertThat(receiver.getRelation()).isNull();
        assertThat(receiver.getMessage()).isNull();

        ArgumentCaptor<UserReceiver> linkCaptor = ArgumentCaptor.forClass(UserReceiver.class);
        verify(userReceiverRepository).save(linkCaptor.capture());
        assertThat(linkCaptor.getValue().getUser().getId()).isEqualTo(1L);
        assertThat(invitation.getAcceptedUserId()).isEqualTo(2L);
        assertThat(invitation.getReceiverId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("초대한 사용자는 자신의 초대를 수락할 수 없다")
    void acceptInvitation_selfAccept() {
        ReceiverInvitation invitation = invitation(1L, now().plusDays(1));
        given(receiverInvitationRepository.findByTokenHashForUpdate(any())).willReturn(Optional.of(invitation));

        assertThatThrownBy(() -> receiverInvitationService.acceptInvitation(1L, "raw-token"))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RECEIVER_INVITATION_SELF_ACCEPT);
        verify(receiverRepository, never()).save(any());
    }

    @Test
    @DisplayName("만료된 초대는 수락할 수 없다")
    void acceptInvitation_expired() {
        ReceiverInvitation invitation = invitation(1L, now().minusSeconds(1));
        given(receiverInvitationRepository.findByTokenHashForUpdate(any())).willReturn(Optional.of(invitation));

        assertThatThrownBy(() -> receiverInvitationService.acceptInvitation(2L, "raw-token"))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RECEIVER_INVITATION_EXPIRED);
        verify(receiverRepository, never()).save(any());
    }

    private static ReceiverInvitation invitation(Long inviterId, LocalDateTime expiresAt) {
        return new ReceiverInvitation(inviterId, "a".repeat(64), expiresAt);
    }

    private static LocalDateTime now() {
        return LocalDateTime.ofInstant(NOW, SEOUL);
    }

    private static User user(Long id, String email, String name, String phone) {
        User user = User.builder()
                .email(email)
                .password("pw")
                .name(name)
                .phone(phone)
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
