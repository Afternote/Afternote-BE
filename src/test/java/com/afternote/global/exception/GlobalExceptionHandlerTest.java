package com.afternote.global.exception;

import com.afternote.global.common.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

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
