package com.afternote.global.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeUniquenessTest {

    @Test
    @DisplayName("클라이언트가 오류를 정확히 분기할 수 있도록 에러 코드는 중복되지 않는다")
    void errorCodes_areUnique() {
        Map<Integer, Long> counts = Arrays.stream(ErrorCode.values())
                .collect(Collectors.groupingBy(ErrorCode::getCode, Collectors.counting()));

        assertThat(counts)
                .allSatisfy((code, count) -> assertThat(count)
                        .as("duplicated error code: %s", code)
                        .isEqualTo(1L));
    }
}
