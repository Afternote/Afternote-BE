package com.afternote.global.util;

import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNumbersTest {

    @Test
    @DisplayName("유효한 휴대폰 형식 통과")
    void validPhones() {
        assertThatCode(() -> PhoneNumbers.validateOptional("010-1234-5678")).doesNotThrowAnyException();
        assertThatCode(() -> PhoneNumbers.validateOptional("01012345678")).doesNotThrowAnyException();
        assertThatCode(() -> PhoneNumbers.validateOptional("010 1234 5678")).doesNotThrowAnyException();
        assertThatCode(() -> PhoneNumbers.validateOptional(null)).doesNotThrowAnyException();
        assertThatCode(() -> PhoneNumbers.validateOptional("")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("잘못된 형식은 INVALID_PHONE_FORMAT")
    void invalidPhones() {
        assertThatThrownBy(() -> PhoneNumbers.validateOptional("abc123"))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_PHONE_FORMAT));
        assertThatThrownBy(() -> PhoneNumbers.validateOptional("1234"))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("normalize 는 숫자만 남긴다")
    void normalize() {
        assertThat(PhoneNumbers.normalize("010-1234-5678")).isEqualTo("01012345678");
    }
}
