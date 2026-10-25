package com.afternote.domain.receiver.controller;

import com.afternote.domain.receiver.service.ReceiverInvitationService;
import com.afternote.global.resolver.UserIdArgumentResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReceiverInvitationControllerTest {

    private static final long USER_ID = 1L;

    @InjectMocks private ReceiverInvitationController controller;
    @Mock private ReceiverInvitationService receiverInvitationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new UserIdArgumentResolver())
                .setControllerAdvice(new com.afternote.global.exception.GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("수신자 초대 링크 생성 API 성공")
    void createInvitation_success() throws Exception {
        mockMvc.perform(post("/api/v1/receiver-invitations")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID))
                .andExpect(status().isOk());

        verify(receiverInvitationService).createInvitation(USER_ID);
    }

    @Test
    @DisplayName("수신자 초대 정보 조회 API 성공")
    void getInvitation_success() throws Exception {
        mockMvc.perform(get("/api/v1/receiver-invitations/{token}", "token"))
                .andExpect(status().isOk());

        verify(receiverInvitationService).getInvitation("token");
    }

    @Test
    @DisplayName("수신자 초대 수락 API 성공")
    void acceptInvitation_success() throws Exception {
        mockMvc.perform(post("/api/v1/receiver-invitations/{token}/accept", "token")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID))
                .andExpect(status().isOk());

        verify(receiverInvitationService).acceptInvitation(USER_ID, "token");
    }

    @Test void declineReturnsEmptySuccessEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/receiver-invitations/token/decline")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(200))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
        verify(receiverInvitationService).declineInvitation(USER_ID, "token");
    }
    @Test void declineRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/v1/receiver-invitations/token/decline")).andExpect(status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(receiverInvitationService);
    }
    @Test void ownedLookupRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/v1/receiver-invitations/owned/1")).andExpect(status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(receiverInvitationService);
    }
    @Test void targetedCreateDelegatesOwnerAndReceiver() throws Exception {
        mockMvc.perform(post("/api/v1/receiver-invitations/receivers/12")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID))
                .andExpect(status().isOk());
        verify(receiverInvitationService).createTargetedInvitation(USER_ID, 12L);
    }
    @Test void expiredDeclineReturns410AndDomainCode() throws Exception {
        org.mockito.Mockito.doThrow(new com.afternote.global.exception.CustomException(
                com.afternote.global.exception.ErrorCode.RECEIVER_INVITATION_EXPIRED))
                .when(receiverInvitationService).declineInvitation(USER_ID, "token");
        mockMvc.perform(post("/api/v1/receiver-invitations/token/decline")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID))
                .andExpect(status().isGone())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(1906));
    }
}
