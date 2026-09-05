package com.afternote.global.exception;

import com.afternote.global.common.ApiResponse;
import io.sentry.Sentry;
import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.StaleObjectStateException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. CustomException 처리
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
        if (e.getErrorCode().getHttpStatus().is5xxServerError()) {
            Sentry.captureException(e);
        }
        return ResponseEntity
                .status(e.getErrorCode().getHttpStatus())
                .body(ApiResponse.error(e.getErrorCode()));
    }

    // 2. @Valid 검증 실패 처리 (code: 1400)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(400, ErrorCode.INVALID_INPUT_VALUE.getCode(), errorMessage));
    }

    // 2-1. @Validated + @Min/@Max 등 메서드 파라미터 검증
    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(Exception e) {
        String errorMessage = "요청 값이 올바르지 않습니다.";
        if (e instanceof ConstraintViolationException cve && !cve.getConstraintViolations().isEmpty()) {
            errorMessage = cve.getConstraintViolations().iterator().next().getMessage();
        }
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(400, ErrorCode.INVALID_INPUT_VALUE.getCode(), errorMessage));
    }

    // 3. JSON 파싱 에러 처리 (잘못된 형식의 요청, code: 1400)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleJsonParseException(HttpMessageNotReadableException e) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(
                        400,
                        ErrorCode.INVALID_INPUT_VALUE.getCode(),
                        "잘못된 요청 형식입니다. JSON 데이터를 확인해주세요."
                ));
    }

    // 4. 경로 변수/쿼리 파라미터 타입 불일치 (예: yearMonth=, page 문자열)
    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            TypeMismatchException.class,
            ConversionFailedException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatchException(Exception e) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(400, ErrorCode.INVALID_INPUT_VALUE.getCode(), "요청 파라미터 형식이 올바르지 않습니다."));
    }

    // 4-1. 필수 헤더/쿼리 파라미터 누락 — 폴백 500/Sentry 로 보내지 않는다
    @ExceptionHandler({
            MissingRequestHeaderException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestValue(Exception e) {
        String message;
        if (e instanceof MissingRequestHeaderException headerEx) {
            message = "필수 요청 헤더가 없습니다: " + headerEx.getHeaderName();
        } else if (e instanceof MissingServletRequestParameterException paramEx) {
            message = "필수 요청 파라미터가 없습니다: " + paramEx.getParameterName();
        } else {
            message = "필수 요청 값이 없습니다.";
        }
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(400, ErrorCode.INVALID_INPUT_VALUE.getCode(), message));
    }

    // 5. 허용되지 않은 HTTP 메서드 (예: POST만 있는 path에 DELETE)
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        ErrorCode errorCode = ErrorCode.METHOD_NOT_ALLOWED;
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode));
    }

    // 5-1. 존재하지 않는 경로
    // Spring Boot 3.2+ 는 ResourceHttpRequestHandler 가 NoResourceFoundException 을 던지고,
    // 이를 잡지 않으면 아래 Exception 핸들러가 500/1004 로 삼킨다.
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception e) {
        ErrorCode errorCode = ErrorCode.ENDPOINT_NOT_FOUND;
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode));
    }

    /**
     * 동시 회원가입 등: exists 검사 통과 후 unique 제약에 걸린 경우.
     * DB 무결성은 지켜지므로 500 대신 중복 이메일(409)로 응답한다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        if (looksLikeDuplicateEmail(e)) {
            log.info("Duplicate email constraint: {}", rootMessage(e));
            ErrorCode errorCode = ErrorCode.DUPLICATE_EMAIL;
            return ResponseEntity
                    .status(errorCode.getHttpStatus())
                    .body(ApiResponse.error(errorCode));
        }
        log.error("Data integrity violation", e);
        Sentry.captureException(e);
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode));
    }

    /**
     * 동시 삭제: 한쪽이 이미 지운 행을 다른 쪽이 delete 할 때 row count 0 / stale.
     * 500 대신 404(이미 없음)로 응답한다.
     */
    @ExceptionHandler({
            ObjectOptimisticLockingFailureException.class,
            OptimisticLockingFailureException.class,
            OptimisticLockException.class,
            StaleObjectStateException.class,
            EmptyResultDataAccessException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleConcurrentDelete(Exception e) {
        log.info("Concurrent modification / already deleted: {}", e.toString());
        ErrorCode errorCode = ErrorCode.RESOURCE_ALREADY_DELETED;
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode));
    }

    // 6. 그 외 예상치 못한 에러 — 상세는 로그에만, 응답은 일반 메시지
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        Sentry.captureException(e);
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode));
    }

    private static boolean looksLikeDuplicateEmail(DataIntegrityViolationException e) {
        String msg = rootMessage(e).toLowerCase();
        return msg.contains("email")
                || msg.contains("users.email")
                || msg.contains("uk_users")
                || (msg.contains("duplicate") && msg.contains("users"));
    }

    private static String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? "" : cur.getMessage();
    }

}
