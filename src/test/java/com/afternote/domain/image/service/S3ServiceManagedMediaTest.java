package com.afternote.domain.image.service;

import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class S3ServiceManagedMediaTest {

    @Mock
    private S3Presigner s3Presigner;
    @Mock
    private S3Client s3Client;

    private S3Service s3Service;

    @BeforeEach
    void setUp() {
        s3Service = new S3Service(s3Presigner, s3Client);
        ReflectionTestUtils.setField(s3Service, "bucket", "afternote-bucket");
        ReflectionTestUtils.setField(s3Service, "region", "ap-northeast-2");
    }

    @Test
    @DisplayName("afternotes 키는 관리 객체로 본다")
    void managedAfternotesKey_IsAccepted() {
        assertThat(s3Service.isManagedObjectKeyInDirectory(
                "afternotes/staging/1/uuid.jpg", "afternotes")).isTrue();
        assertThat(s3Service.isManagedMediaInDirectory(
                "afternotes/permanent/1/uuid.m4a", "afternotes", S3Service.MediaKind.AUDIO)).isTrue();
    }

    @Test
    @DisplayName("위험 스킴·외부 URL 은 관리 객체가 아니다")
    void unmanagedUrls_AreRejected() {
        assertThat(s3Service.isManagedObjectKeyInDirectory("javascript:alert(1)", "afternotes")).isFalse();
        assertThat(s3Service.isManagedObjectKeyInDirectory("http://evil.example/a.jpg", "afternotes")).isFalse();
        assertThat(s3Service.isManagedObjectKeyInDirectory(
                "https://evil.example/a.jpg", "afternotes")).isFalse();
        assertThat(s3Service.isManagedObjectKeyInDirectory("timeletters/staging/1/a.jpg", "afternotes")).isFalse();
    }

    @Test
    @DisplayName("관리 키가 아니면 promoteManagedMediaKey 는 1805")
    void promoteManagedMediaKey_RejectsUnmanaged() {
        assertThatThrownBy(() -> s3Service.promoteManagedMediaKey(
                "afternotes", 1L, "javascript:alert(1)"))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.UNMANAGED_MEDIA_URL));
    }

    @Test
    @DisplayName("슬롯과 다른 확장자면 1801")
    void promoteManagedMediaKey_RejectsWrongExtension() {
        assertThatThrownBy(() -> s3Service.promoteManagedMediaKey(
                "afternotes", 1L, "afternotes/staging/1/a.m4a", S3Service.MediaKind.IMAGE))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_FILE_EXTENSION));
    }

    @Test
    @DisplayName("업로드되지 않은 staging 키 승격은 400/1807")
    void promoteManagedMediaKey_MissingSource_Is400() {
        given(s3Client.copyObject(any(CopyObjectRequest.class))).willThrow(s3Exception(404, "NoSuchKey"));

        assertThatThrownBy(() -> s3Service.promoteManagedMediaKey(
                "afternotes", 1L, "afternotes/staging/1/voice.m4a", S3Service.MediaKind.AUDIO))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> {
                    CustomException ce = (CustomException) ex;
                    assertThat(ce.getErrorCode()).isEqualTo(ErrorCode.MEDIA_NOT_UPLOADED);
                    assertThat(ce.getErrorCode().getHttpStatus().value()).isEqualTo(400);
                    assertThat(ce.getErrorCode().getCode()).isEqualTo(1807);
                });
    }

    @Test
    @DisplayName("버킷 부재 등 인프라 오류는 500/1808 이고 1807이 아니다")
    void promoteManagedMediaKey_InfraFailure_Is500() {
        given(s3Client.copyObject(any(CopyObjectRequest.class))).willThrow(s3Exception(404, "NoSuchBucket"));

        assertThatThrownBy(() -> s3Service.promoteManagedMediaKey(
                "afternotes", 1L, "afternotes/staging/1/voice.m4a", S3Service.MediaKind.AUDIO))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> {
                    CustomException ce = (CustomException) ex;
                    assertThat(ce.getErrorCode()).isEqualTo(ErrorCode.MEDIA_PROMOTE_FAILED);
                    assertThat(ce.getErrorCode().getHttpStatus().value()).isEqualTo(500);
                    assertThat(ce.getErrorCode().getCode()).isEqualTo(1808);
                });
    }

    @Test
    @DisplayName("S3 삭제 실패는 1806을 내지 않고 요청을 통과시킨다")
    void deleteManagedObject_S3Failure_DoesNotThrow() {
        willThrow(s3Exception(500, "InternalError"))
                .given(s3Client).deleteObject(any(DeleteObjectRequest.class));

        assertThatCode(() -> s3Service.deleteManagedObject(
                "afternotes/permanent/1/old.jpg", "afternotes"))
                .doesNotThrowAnyException();
    }

    private static S3Exception s3Exception(int statusCode, String errorCode) {
        return (S3Exception) S3Exception.builder()
                .statusCode(statusCode)
                .awsErrorDetails(AwsErrorDetails.builder()
                        .errorCode(errorCode)
                        .errorMessage(errorCode)
                        .build())
                .build();
    }
}
