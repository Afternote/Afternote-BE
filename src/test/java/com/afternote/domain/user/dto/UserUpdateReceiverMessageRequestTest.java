package com.afternote.domain.user.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class UserUpdateReceiverMessageRequestTest {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    @Test
    @DisplayName("message를 생략하면 미지정이다")
    void omittedMessage_IsUnspecified() throws Exception {
        UserUpdateReceiverMessageRequest request = objectMapper.readValue("{}", UserUpdateReceiverMessageRequest.class);

        assertThat(request.isMessageSpecified()).isFalse();
        assertThat(request.getMessage()).isNull();
    }

    @Test
    @DisplayName("명시적 null은 비우기로 지정된다")
    void explicitNull_Clears() throws Exception {
        UserUpdateReceiverMessageRequest request = objectMapper.readValue(
                "{\"message\":null}", UserUpdateReceiverMessageRequest.class);

        assertThat(request.isMessageSpecified()).isTrue();
        assertThat(request.getMessage()).isNull();
    }

    @Test
    @DisplayName("공백 문자열도 null로 정규화한다")
    void blank_NormalizesToNull() throws Exception {
        UserUpdateReceiverMessageRequest request = objectMapper.readValue(
                "{\"message\":\"  \"}", UserUpdateReceiverMessageRequest.class);

        assertThat(request.isMessageSpecified()).isTrue();
        assertThat(request.getMessage()).isNull();
    }

    @Test
    @DisplayName("값이 있으면 그 문자열을 유지한다")
    void value_IsKept() throws Exception {
        UserUpdateReceiverMessageRequest request = objectMapper.readValue(
                "{\"message\":\"사랑하는 딸에게\"}", UserUpdateReceiverMessageRequest.class);

        assertThat(request.isMessageSpecified()).isTrue();
        assertThat(request.getMessage()).isEqualTo("사랑하는 딸에게");
    }
}
