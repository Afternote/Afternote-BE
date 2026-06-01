package com.afternote.global.common;

import com.afternote.global.jwt.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTokenExpiresInAdviceTest {

    private final AccessTokenExpiresInAdvice advice = new AccessTokenExpiresInAdvice();

    @Test
    @DisplayName("@IncludeAccessTokenExpiresIn가 붙은 ApiResponse에 expiresIn을 주입한다")
    void beforeBodyWrite_addsExpiresIn() throws Exception {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setAttribute(JwtAuthenticationFilter.ACCESS_TOKEN_EXPIRES_IN_ATTRIBUTE, 1800L);
        ServletServerHttpRequest request = new ServletServerHttpRequest(servletRequest);

        ApiResponse<String> body = ApiResponse.success("ok");
        MethodParameter returnType = new MethodParameter(
                SampleController.class.getMethod("sample", String.class),
                -1
        );

        ApiResponse<?> result = advice.beforeBodyWrite(
                body,
                returnType,
                MediaType.APPLICATION_JSON,
                StringHttpMessageConverter.class,
                request,
                null
        );

        assertThat(result.getExpiresIn()).isEqualTo(1800L);
        assertThat(result.getData()).isEqualTo("ok");
    }

    static class SampleController {
        @IncludeAccessTokenExpiresIn
        public ApiResponse<String> sample(String ignored) {
            return ApiResponse.success("ok");
        }
    }
}
