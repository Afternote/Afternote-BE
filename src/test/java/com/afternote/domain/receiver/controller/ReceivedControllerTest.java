package com.afternote.domain.receiver.controller;

import com.afternote.domain.receiver.service.ReceivedService;
import com.afternote.global.resolver.UserId;
import com.afternote.global.resolver.UserIdArgumentResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReceivedControllerTest {

    private static final long USER_ID = 1L;

    @InjectMocks
    private ReceivedController receivedController;

    @Mock
    private ReceivedService receivedService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(receivedController)
                .setCustomArgumentResolvers(new UserIdTestArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("타임레터 수신자 등록 API 성공")
    void createTimeLetterReceivers_Success() throws Exception {
        given(receivedService.createTimeLetterReceivers(eq(USER_ID), any())).willReturn(List.of(1L, 2L));

        mockMvc.perform(post("/api/received/time-letters")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeLetterID\":1,\"receiverIds\":[1,2]}"))
                .andExpect(status().isOk());

        verify(receivedService).createTimeLetterReceivers(eq(USER_ID), any());
    }

    @Test
    @DisplayName("타임레터 수신자 등록 API 실패 - receiverIds 누락")
    void createTimeLetterReceivers_MissingReceiverIds_Fail() throws Exception {
        mockMvc.perform(post("/api/received/time-letters")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeLetterID\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("마인드레코드 수신자 등록 API 성공")
    void createMindRecordReceivers_Success() throws Exception {
        given(receivedService.createMindRecordReceivers(eq(USER_ID), any())).willReturn(List.of(1L));

        mockMvc.perform(post("/api/received/mind-records")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mindRecordId\":1,\"receiverIds\":[1]}"))
                .andExpect(status().isOk());

        verify(receivedService).createMindRecordReceivers(eq(USER_ID), any());
    }

    private static class UserIdTestArgumentResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(UserId.class)
                    && Long.class.isAssignableFrom(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            Object userId = webRequest.getAttribute(UserIdArgumentResolver.USER_ID_ATTRIBUTE, NativeWebRequest.SCOPE_REQUEST);
            return userId == null ? USER_ID : userId;
        }
    }
}
