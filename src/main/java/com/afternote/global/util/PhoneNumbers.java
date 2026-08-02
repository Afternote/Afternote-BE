package com.afternote.global.util;

import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;

import java.util.regex.Pattern;

/**
 * 수신자/연락처용 국내 휴대폰 번호 유틸.
 * 허용: 01012345678, 010-1234-5678, 010 1234 5678 등 (숫자 10~11자리, 01X 시작)
 */
public final class PhoneNumbers {

    private static final Pattern MOBILE_DIGITS = Pattern.compile("^01[016789]\\d{7,8}$");

    private PhoneNumbers() {
    }

    public static String normalize(String phone) {
        if (phone == null) {
            return null;
        }
        return phone.replaceAll("[^0-9]", "");
    }

    public static boolean isBlank(String phone) {
        return phone == null || phone.isBlank();
    }

    /**
     * phone 이 비어 있으면 통과(선택 필드). 값이 있으면 형식 검증.
     */
    public static void validateOptional(String phone) {
        if (isBlank(phone)) {
            return;
        }
        validateRequired(phone);
    }

    public static void validateRequired(String phone) {
        if (isBlank(phone) || !MOBILE_DIGITS.matcher(normalize(phone)).matches()) {
            throw new CustomException(ErrorCode.INVALID_PHONE_FORMAT);
        }
    }
}
