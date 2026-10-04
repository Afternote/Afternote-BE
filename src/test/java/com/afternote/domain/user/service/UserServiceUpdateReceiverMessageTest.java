package com.afternote.domain.user.service;

import com.afternote.domain.auth.service.TokenService;
import com.afternote.domain.auth.service.social.SocialLoginFactory;
import com.afternote.domain.image.service.S3Service;
import com.afternote.domain.receiver.model.Receiver;
import com.afternote.domain.receiver.model.UserReceiver;
import com.afternote.domain.receiver.repository.ReceiverRepository;
import com.afternote.domain.receiver.repository.UserReceiverRepository;
import com.afternote.domain.user.dto.UserUpdateReceiverMessageRequest;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.repository.UserProviderRepository;
import com.afternote.domain.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserServiceUpdateReceiverMessageTest {

    private static final long USER_ID = 1L;
    private static final long RECEIVER_ID = 2L;

    @InjectMocks
    private UserService userService;

    @Mock private UserRepository userRepository;
    @Mock private UserReceiverRepository userReceiverRepository;
    @Mock private ReceiverRepository receiverRepository;
    @Mock private S3Service s3Service;
    @Mock private TokenService tokenService;
    @Mock private SocialLoginFactory socialLoginFactory;
    @Mock private UserProviderRepository userProviderRepository;
    @Mock private AccountWithdrawalService accountWithdrawalService;
    @Mock private ReceiverDeletionService receiverDeletionService;
    @Mock private ActivityTouchService activityTouchService;

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    private Receiver receiver;

    @BeforeEach
    void setUp() {
        User user = User.builder().email("sender@test.com").name("발신자").build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        receiver = Receiver.builder()
                .name("수신자")
                .relation("DAUGHTER")
                .phone("010-1111-2222")
                .email("receiver@test.com")
                .message("기존 메시지")
                .userId(USER_ID)
                .build();
        ReflectionTestUtils.setField(receiver, "id", RECEIVER_ID);
        UserReceiver link = UserReceiver.builder().user(user).receiver(receiver).build();

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(userReceiverRepository.findByUserAndReceiverId(user, RECEIVER_ID)).willReturn(Optional.of(link));
    }

    @Test
    @DisplayName("메시지를 지정하면 그 값으로 바꾸고 이름·연락처·ID는 유지한다")
    void specifiedValue_UpdatesMessageOnly() throws Exception {
        userService.updateReceiverMessage(USER_ID, RECEIVER_ID, request("{\"message\":\"새 메시지\"}"));

        assertThat(receiver.getMessage()).isEqualTo("새 메시지");
        assertIdentityUnchanged();
    }

    @Test
    @DisplayName("명시적 null은 메시지를 null로 비운다")
    void explicitNull_ClearsMessage() throws Exception {
        userService.updateReceiverMessage(USER_ID, RECEIVER_ID, request("{\"message\":null}"));

        assertThat(receiver.getMessage()).isNull();
        assertIdentityUnchanged();
    }

    @Test
    @DisplayName("빈 문자열은 null로 저장한다")
    void blank_ClearsMessage() throws Exception {
        userService.updateReceiverMessage(USER_ID, RECEIVER_ID, request("{\"message\":\"\"}"));

        assertThat(receiver.getMessage()).isNull();
        assertIdentityUnchanged();
    }

    @Test
    @DisplayName("message를 생략하면 기존 메시지를 유지한다")
    void omitted_KeepsMessage() throws Exception {
        userService.updateReceiverMessage(USER_ID, RECEIVER_ID, request("{}"));

        assertThat(receiver.getMessage()).isEqualTo("기존 메시지");
        assertIdentityUnchanged();
    }

    private UserUpdateReceiverMessageRequest request(String json) throws Exception {
        return objectMapper.readValue(json, UserUpdateReceiverMessageRequest.class);
    }

    private void assertIdentityUnchanged() {
        assertThat(receiver.getId()).isEqualTo(RECEIVER_ID);
        assertThat(receiver.getName()).isEqualTo("수신자");
        assertThat(receiver.getRelation()).isEqualTo("DAUGHTER");
        assertThat(receiver.getPhone()).isEqualTo("010-1111-2222");
        assertThat(receiver.getEmail()).isEqualTo("receiver@test.com");
        assertThat(receiver.getAfternoteReceivers()).isEmpty();
    }
}
