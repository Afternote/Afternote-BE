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
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
