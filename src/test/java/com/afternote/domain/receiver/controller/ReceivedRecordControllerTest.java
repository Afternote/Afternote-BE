package com.afternote.domain.receiver.controller;

import com.afternote.domain.receiver.service.ReceivedRecordService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReceivedRecordControllerTest {

    private static final long USER_ID = 1L;
    private static final long RECEIVER_ID = 2L;

    @InjectMocks private ReceivedRecordController controller;
    @Mock private ReceivedRecordService receivedRecordService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new UserIdTestArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("받은 기록함 목록은 로그인한 회원 기준으로 조회한다")
    void getRecordBoxes_Success() throws Exception {
        mockMvc.perform(get("/api/v1/received-records")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID))
                .andExpect(status().isOk());

        verify(receivedRecordService).getRecordBoxes(USER_ID);
    }

    @Test
    @DisplayName("받은 타임레터는 로그인한 회원과 수신자 ID로 조회한다")
    void getTimeLetters_Success() throws Exception {
        mockMvc.perform(get("/api/v1/received-records/{receiverId}/time-letters", RECEIVER_ID)
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID))
                .andExpect(status().isOk());

        verify(receivedRecordService).getTimeLetters(USER_ID, RECEIVER_ID);
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
                    NativeWebRequest.SCOPE_REQUEST);
        }
    }
}
