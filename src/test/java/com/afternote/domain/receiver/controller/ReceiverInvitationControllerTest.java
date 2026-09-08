package com.afternote.domain.receiver.controller;

import com.afternote.domain.receiver.service.ReceiverInvitationService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

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
                .setCustomArgumentResolvers(new UserIdTestArgumentResolver())
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

    private static class UserIdTestArgumentResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(UserId.class)
                    && Long.class.isAssignableFrom(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory
        ) {
            return webRequest.getAttribute(
                    UserIdArgumentResolver.USER_ID_ATTRIBUTE,
                    NativeWebRequest.SCOPE_REQUEST
            );
        }
    }
}
