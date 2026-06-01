package com.afternote.domain.auth.dto;

public final class PasswordValidation {

    public static final String REGEX =
            "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,15}$";

    public static final String MESSAGE =
            "비밀번호는 8~15자의 영문, 숫자, 특수문자를 포함해야 합니다.";

    private PasswordValidation() {
    }
}
