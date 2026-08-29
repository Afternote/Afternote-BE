package com.afternote.global.exception;

import com.afternote.global.common.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("@Valid 실패는 HTTP 400 / code 1400")
    void validationFailure_usesInvalidInputValueCode() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "title", "제목은 필수입니다."));
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        given(exception.getBindingResult()).willReturn(bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidationException(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE.getCode());
        assertThat(response.getBody().getMessage()).isEqualTo("제목은 필수입니다.");
    }

    @Test
    @DisplayName("JSON·날짜 형식 오류는 HTTP 400 / code 1400 (미래 날짜 2101과 구분)")
    void jsonParseFailure_usesInvalidInputValueCode() {
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException(
                "JSON parse error", mock(HttpInputMessage.class));

        ResponseEntity<ApiResponse<Void>> response = handler.handleJsonParseException(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE.getCode());
        assertThat(response.getBody().getMessage()).isEqualTo("잘못된 요청 형식입니다. JSON 데이터를 확인해주세요.");
    }

    @Test
    @DisplayName("허용되지 않은 메서드는 405 / 1005 — HTTP status 와 봉투 status 일치")
    void methodNotAllowed() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("DELETE")
        );

        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(405);
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.METHOD_NOT_ALLOWED.getCode());
        assertThat(response.getBody().getMessage()).isEqualTo(ErrorCode.METHOD_NOT_ALLOWED.getMessage());
    }

    @Test
    @DisplayName("존재하지 않는 경로는 404 / 1003 (NoResourceFoundException) — Boot 3.2+ 라우팅 404")
    void noResourceFound() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleNotFound(
                new NoResourceFoundException(HttpMethod.GET, "api/v1/no-such-endpoint")
        );

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.ENDPOINT_NOT_FOUND.getCode());
        assertThat(response.getBody().getMessage()).isEqualTo(ErrorCode.ENDPOINT_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("존재하지 않는 경로는 404 / 1003 (NoHandlerFoundException)")
    void noHandlerFound() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleNotFound(
                new NoHandlerFoundException("GET", "/api/v1/no-such-endpoint", null)
        );

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.ENDPOINT_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("이메일 unique 제약은 409 / 1200 DUPLICATE_EMAIL")
    void duplicateEmailConstraint() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrity(
                new DataIntegrityViolationException(
                        "Duplicate entry 'a@test.com' for key 'users.email'"
                )
        );

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.DUPLICATE_EMAIL.getCode());
    }

    @Test
    @DisplayName("동시 삭제 stale/empty 는 404 / 1006")
    void concurrentDelete() {
        ResponseEntity<ApiResponse<Void>> optimistic = handler.handleConcurrentDelete(
                new ObjectOptimisticLockingFailureException("Afternote", 1L)
        );
        ResponseEntity<ApiResponse<Void>> empty = handler.handleConcurrentDelete(
                new EmptyResultDataAccessException(1)
        );

        assertThat(optimistic.getStatusCode().value()).isEqualTo(404);
        assertThat(optimistic.getBody().getCode()).isEqualTo(ErrorCode.RESOURCE_ALREADY_DELETED.getCode());
        assertThat(empty.getStatusCode().value()).isEqualTo(404);
        assertThat(empty.getBody().getCode()).isEqualTo(ErrorCode.RESOURCE_ALREADY_DELETED.getCode());
    }
}
