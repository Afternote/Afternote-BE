package com.afternote.domain.receiver.controller;

import com.afternote.domain.receiver.service.ReceiverAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReceiverAuthControllerTest {

    private static final String AUTH_CODE = "550e8400-e29b-41d4-a716-446655440000";

    @InjectMocks
    private ReceiverAuthController receiverAuthController;

    @Mock
    private ReceiverAuthService receiverAuthService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(receiverAuthController).build();
    }

    @Test
    @DisplayName("인증번호 검증 API 성공")
    void verifyAuthCode_Success() throws Exception {
        given(receiverAuthService.verifyAuthCode(AUTH_CODE)).willReturn(null);

        mockMvc.perform(post("/api/receiver-auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authCode\":\"" + AUTH_CODE + "\"}"))
                .andExpect(status().isOk());

        verify(receiverAuthService).verifyAuthCode(AUTH_CODE);
    }

    @Test
    @DisplayName("인증번호 검증 API 실패 - UUID 형식 오류")
    void verifyAuthCode_InvalidFormat_Fail() throws Exception {
        mockMvc.perform(post("/api/receiver-auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authCode\":\"not-uuid\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("타임레터 목록 조회 API 성공")
    void getTimeLetters_Success() throws Exception {
        given(receiverAuthService.getTimeLettersByAuthCode(AUTH_CODE)).willReturn(null);

        mockMvc.perform(get("/api/receiver-auth/time-letters").header("X-Auth-Code", AUTH_CODE))
                .andExpect(status().isOk());

        verify(receiverAuthService).getTimeLettersByAuthCode(AUTH_CODE);
    }

    @Test
    @DisplayName("애프터노트 목록 조회 API 성공")
    void getAfternotes_Success() throws Exception {
        given(receiverAuthService.getAfternotesByAuthCode(AUTH_CODE)).willReturn(null);

        mockMvc.perform(get("/api/receiver-auth/after-notes").header("X-Auth-Code", AUTH_CODE))
                .andExpect(status().isOk());

        verify(receiverAuthService).getAfternotesByAuthCode(AUTH_CODE);
    }

    @Test
    @DisplayName("마인드레코드 목록 조회 API 성공")
    void getMindRecords_Success() throws Exception {
        given(receiverAuthService.getMindRecordsByAuthCode(AUTH_CODE)).willReturn(null);

        mockMvc.perform(get("/api/receiver-auth/mind-records").header("X-Auth-Code", AUTH_CODE))
                .andExpect(status().isOk());

        verify(receiverAuthService).getMindRecordsByAuthCode(AUTH_CODE);
    }

    @Test
    @DisplayName("타임레터 상세 조회 API 성공")
    void getTimeLetter_Success() throws Exception {
        given(receiverAuthService.getTimeLetterByAuthCode(AUTH_CODE, 1L)).willReturn(null);

        mockMvc.perform(get("/api/receiver-auth/time-letters/{id}", 1L).header("X-Auth-Code", AUTH_CODE))
                .andExpect(status().isOk());

        verify(receiverAuthService).getTimeLetterByAuthCode(AUTH_CODE, 1L);
    }

    @Test
    @DisplayName("마인드레코드 상세 조회 API 성공")
    void getMindRecord_Success() throws Exception {
        given(receiverAuthService.getMindRecordByAuthCode(AUTH_CODE, 2L)).willReturn(null);

        mockMvc.perform(get("/api/receiver-auth/mind-records/{id}", 2L).header("X-Auth-Code", AUTH_CODE))
                .andExpect(status().isOk());

        verify(receiverAuthService).getMindRecordByAuthCode(AUTH_CODE, 2L);
    }

    @Test
    @DisplayName("애프터노트 상세 조회 API 성공")
    void getAfternote_Success() throws Exception {
        given(receiverAuthService.getAfternoteByAuthCode(AUTH_CODE, 3L)).willReturn(null);

        mockMvc.perform(get("/api/receiver-auth/after-notes/{id}", 3L).header("X-Auth-Code", AUTH_CODE))
                .andExpect(status().isOk());

        verify(receiverAuthService).getAfternoteByAuthCode(AUTH_CODE, 3L);
    }

    @Test
    @DisplayName("메시지 조회 API 성공")
    void getMessage_Success() throws Exception {
        given(receiverAuthService.getMessageByAuthCode(AUTH_CODE)).willReturn(null);

        mockMvc.perform(get("/api/receiver-auth/message").header("X-Auth-Code", AUTH_CODE))
                .andExpect(status().isOk());

        verify(receiverAuthService).getMessageByAuthCode(AUTH_CODE);
    }

    @Test
    @DisplayName("수신자 Presigned URL API 성공")
    void getPresignedUrl_Success() throws Exception {
        given(receiverAuthService.generatePresignedUrl(AUTH_CODE, "pdf")).willReturn(null);

        mockMvc.perform(post("/api/receiver-auth/presigned-url")
                        .header("X-Auth-Code", AUTH_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"extension\":\"pdf\"}"))
                .andExpect(status().isOk());

        verify(receiverAuthService).generatePresignedUrl(AUTH_CODE, "pdf");
    }

    @Test
    @DisplayName("사망확인 서류 제출 API 성공")
    void submitDeliveryVerification_Success() throws Exception {
        given(receiverAuthService.submitDeliveryVerification(org.mockito.ArgumentMatchers.eq(AUTH_CODE), any())).willReturn(null);

        mockMvc.perform(post("/api/receiver-auth/delivery-verification")
                        .header("X-Auth-Code", AUTH_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deathCertificateUrl\":\"https://a\",\"familyRelationCertificateUrl\":\"https://b\"}"))
                .andExpect(status().isOk());

        verify(receiverAuthService).submitDeliveryVerification(org.mockito.ArgumentMatchers.eq(AUTH_CODE), any());
    }

    @Test
    @DisplayName("사망확인 상태 조회 API 성공")
    void getDeliveryVerificationStatus_Success() throws Exception {
        given(receiverAuthService.getDeliveryVerificationStatus(AUTH_CODE)).willReturn(null);

        mockMvc.perform(get("/api/receiver-auth/delivery-verification/status").header("X-Auth-Code", AUTH_CODE))
                .andExpect(status().isOk());

        verify(receiverAuthService).getDeliveryVerificationStatus(AUTH_CODE);
    }
}
