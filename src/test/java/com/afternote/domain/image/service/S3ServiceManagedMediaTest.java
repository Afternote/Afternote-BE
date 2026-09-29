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
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

    @Test
    @DisplayName("탈퇴 퍼지는 해당 userId의 staging·permanent prefix 만 지운다")
    void deleteAllOwnedByUser_DeletesOwnerPrefixesOnly() {
        given(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).willAnswer(invocation -> {
            ListObjectsV2Request request = invocation.getArgument(0);
            if ("profiles/permanent/10/".equals(request.prefix())) {
                return ListObjectsV2Response.builder()
                        .contents(S3Object.builder().key("profiles/permanent/10/a.jpg").build())
                        .isTruncated(false)
                        .build();
            }
            return ListObjectsV2Response.builder().isTruncated(false).build();
        });

        s3Service.deleteAllOwnedByUser(10L);

        org.mockito.ArgumentCaptor<ListObjectsV2Request> listCaptor =
                org.mockito.ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client, org.mockito.Mockito.atLeastOnce()).listObjectsV2(listCaptor.capture());
        Set<String> prefixes = listCaptor.getAllValues().stream()
                .map(ListObjectsV2Request::prefix)
                .collect(Collectors.toSet());
        assertThat(prefixes).containsExactlyInAnyOrder(
                "profiles/staging/10/", "profiles/permanent/10/",
                "timeletters/staging/10/", "timeletters/permanent/10/",
                "afternotes/staging/10/", "afternotes/permanent/10/",
                "mindrecords/staging/10/", "mindrecords/permanent/10/",
                "documents/staging/10/", "documents/permanent/10/"
        );
        assertThat(prefixes).noneMatch(prefix -> prefix.contains("/receiver/"));
        assertThat(prefixes).noneMatch(prefix -> prefix.contains("/100/"));

        org.mockito.ArgumentCaptor<DeleteObjectsRequest> deleteCaptor =
                org.mockito.ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(deleteCaptor.capture());
        List<String> deleted = deleteCaptor.getValue().delete().objects().stream()
                .map(id -> id.key())
                .toList();
        assertThat(deleted).containsExactly("profiles/permanent/10/a.jpg");
    }

    @Test
    @DisplayName("탈퇴 퍼지 목록이 잘리면 다음 페이지까지 지운다")
    void deleteAllOwnedByUser_Paginates() {
        ListObjectsV2Response first = ListObjectsV2Response.builder()
                .contents(S3Object.builder().key("afternotes/permanent/7/a.jpg").build())
                .isTruncated(true)
                .nextContinuationToken("next")
                .build();
        ListObjectsV2Response second = ListObjectsV2Response.builder()
                .contents(S3Object.builder().key("afternotes/permanent/7/b.jpg").build())
                .isTruncated(false)
                .build();
        given(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).willAnswer(invocation -> {
            ListObjectsV2Request request = invocation.getArgument(0);
            if (!"afternotes/permanent/7/".equals(request.prefix())) {
                return ListObjectsV2Response.builder().isTruncated(false).build();
            }
            return "next".equals(request.continuationToken()) ? second : first;
        });

        s3Service.deleteAllOwnedByUser(7L);

        org.mockito.ArgumentCaptor<DeleteObjectsRequest> deleteCaptor =
                org.mockito.ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client, org.mockito.Mockito.times(2)).deleteObjects(deleteCaptor.capture());
        List<String> deleted = deleteCaptor.getAllValues().stream()
                .flatMap(req -> req.delete().objects().stream())
                .map(id -> id.key())
                .toList();
        assertThat(deleted).containsExactly(
                "afternotes/permanent/7/a.jpg",
                "afternotes/permanent/7/b.jpg"
        );
    }

    @Test
    @DisplayName("userId 없으면 S3를 호출하지 않는다")
    void deleteAllOwnedByUser_NullUserId_Skipped() {
        s3Service.deleteAllOwnedByUser(null);
        verify(s3Client, never()).listObjectsV2(any(ListObjectsV2Request.class));
        verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test
    @DisplayName("탈퇴 퍼지 S3 실패는 예외를 내지 않는다")
    void deleteAllOwnedByUser_S3Failure_DoesNotThrow() {
        given(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .willThrow(s3Exception(500, "InternalError"));

        assertThatCode(() -> s3Service.deleteAllOwnedByUser(3L)).doesNotThrowAnyException();
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
