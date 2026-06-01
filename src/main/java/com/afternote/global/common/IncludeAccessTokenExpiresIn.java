package com.afternote.global.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 응답 body에 Access Token 남은 만료 시간(expiresIn, 초)을 포함합니다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IncludeAccessTokenExpiresIn {
}
