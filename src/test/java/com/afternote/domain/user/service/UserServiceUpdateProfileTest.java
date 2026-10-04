package com.afternote.domain.user.service;

import com.afternote.domain.auth.service.TokenService;
import com.afternote.domain.auth.service.social.SocialLoginFactory;
import com.afternote.domain.image.service.S3Service;
import com.afternote.domain.receiver.repository.ReceiverRepository;
import com.afternote.domain.receiver.repository.UserReceiverRepository;
import com.afternote.domain.user.dto.UserUpdateProfileRequest;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.repository.UserProviderRepository;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceUpdateProfileTest {

    private static final long USER_ID = 1L;

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

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("user@test.com")
                .name("기존이름")
                .phone("01012345678")
                .profileImageUrl("profiles/old")
                .build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        lenient().when(s3Service.generateGetPresignedUrl("profiles/old")).thenReturn("https://signed/old");
    }

    @Test
    @DisplayName("연락처만 비우면 이름과 프로필 사진은 유지된다")
    void clearPhone_KeepsNameAndImage() throws Exception {
        userService.updateMyProfile(USER_ID, request("{\"phone\":null}"));

        assertThat(user.getPhone()).isNull();
        assertThat(user.getName()).isEqualTo("기존이름");
        assertThat(user.getProfileImageUrl()).isEqualTo("profiles/old");
        verify(s3Service, never()).promoteManagedMediaKey(anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("빈 프로필 이미지 URL은 null로 비운다")
    void blankProfileImage_Clears() throws Exception {
        userService.updateMyProfile(USER_ID, request("{\"profileImageUrl\":\"\"}"));

        assertThat(user.getProfileImageUrl()).isNull();
        assertThat(user.getPhone()).isEqualTo("01012345678");
        assertThat(user.getName()).isEqualTo("기존이름");
    }

    @Test
    @DisplayName("이름만 보내면 연락처와 사진은 유지된다")
    void nameOnly_KeepsOptionalFields() throws Exception {
        userService.updateMyProfile(USER_ID, request("{\"name\":\"새이름\"}"));

        assertThat(user.getName()).isEqualTo("새이름");
        assertThat(user.getPhone()).isEqualTo("01012345678");
        assertThat(user.getProfileImageUrl()).isEqualTo("profiles/old");
    }

    @Test
    @DisplayName("새 프로필 이미지는 승격한 키로 저장한다")
    void newProfileImage_IsPromoted() throws Exception {
        given(s3Service.promoteManagedMediaKey("profiles", USER_ID, "profiles/new")).willReturn("profiles/promoted");
        given(s3Service.generateGetPresignedUrl("profiles/promoted")).willReturn("https://signed/new");

        userService.updateMyProfile(USER_ID, request("{\"profileImageUrl\":\"profiles/new\"}"));

        assertThat(user.getProfileImageUrl()).isEqualTo("profiles/promoted");
        assertThat(user.getPhone()).isEqualTo("01012345678");
        verify(s3Service).promoteManagedMediaKey(eq("profiles"), eq(USER_ID), eq("profiles/new"));
    }

    @Test
    @DisplayName("공백 이름은 거부하고 다른 필드는 바꾸지 않는다")
    void blankName_Rejected() throws Exception {
        UserUpdateProfileRequest request = request("{\"name\":\"  \",\"phone\":null}");

        assertThatThrownBy(() -> userService.updateMyProfile(USER_ID, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        assertThat(user.getName()).isEqualTo("기존이름");
        assertThat(user.getPhone()).isEqualTo("01012345678");
    }

    private UserUpdateProfileRequest request(String json) throws Exception {
        return objectMapper.readValue(json, UserUpdateProfileRequest.class);
    }
}
