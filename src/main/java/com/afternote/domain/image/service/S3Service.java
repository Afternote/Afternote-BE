package com.afternote.domain.image.service;

import com.afternote.domain.image.dto.PresignedUrlResponse;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Presigner s3Presigner;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.s3.region}")
    private String region;

    @Value("${cloud.aws.public-base-url:}")
    private String publicBaseUrl;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "heic",   // 이미지
            "mp4", "mov",                                    // 영상
            "mp3", "m4a", "wav",                             // 음성
            "pdf"                                            // 문서
    );
    private static final Set<String> ALLOWED_DIRECTORIES = Set.of("profiles", "timeletters", "afternotes", "mindrecords", "documents");
    private static final Duration PRESIGNED_URL_EXPIRATION = Duration.ofMinutes(10);
    private static final Duration GET_PRESIGNED_URL_EXPIRATION = Duration.ofHours(1);

    public PresignedUrlResponse generatePresignedUrl(String directory, String extension) {
        String normalizedDir = directory.toLowerCase();
        String normalizedExt = extension.toLowerCase().replaceFirst("^\\.", "");

        validateDirectory(normalizedDir);
        validateExtension(normalizedExt);

        String fileName = UUID.randomUUID() + "." + normalizedExt;
        String key = normalizedDir + "/" + fileName;

        String contentType = getContentType(normalizedExt);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(PRESIGNED_URL_EXPIRATION)
                .putObjectRequest(putObjectRequest)
                .build();

        try {
            PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
            String presignedUrl = presignedRequest.url().toString();
            String fileUrl = resolvePublicUrl(key);
            log.debug("Generate presigned url for file {}", fileUrl);
            return PresignedUrlResponse.builder()
                    .presignedUrl(presignedUrl)
                .fileKey(key)
                    .fileUrl(fileUrl)
                    .contentType(contentType)
                    .build();
        } catch (Exception e) {
            log.error("Presigned URL generation failed for key: {}", key, e);
            throw new CustomException(ErrorCode.PRESIGNED_URL_GENERATION_FAILED);
        }
    }

    public String generateGetPresignedUrl(String rawUrlOrKey) {
        if (!StringUtils.hasText(rawUrlOrKey)) {
            return null;
        }

        String key = extractStorageKey(rawUrlOrKey);
        if (!StringUtils.hasText(key)) {
            return rawUrlOrKey;
        }

        return resolvePublicUrl(key);
    }

    public String extractStorageKey(String rawUrlOrKey) {
        if (!StringUtils.hasText(rawUrlOrKey)) {
            return null;
        }

        String value = rawUrlOrKey.trim();
        String s3Prefix = buildS3Prefix();
        String publicPrefix = buildPublicPrefix();

        if (value.startsWith(s3Prefix)) {
            return sanitizeKey(value.substring(s3Prefix.length()));
        }

        if (StringUtils.hasText(publicPrefix) && value.startsWith(publicPrefix)) {
            return sanitizeKey(value.substring(publicPrefix.length()));
        }

        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            return sanitizeKey(value);
        }

        return null;
    }

    public boolean isManagedObjectKeyInDirectory(String rawUrlOrKey, String directory) {
        if (!StringUtils.hasText(directory)) {
            return false;
        }

        String key = extractStorageKey(rawUrlOrKey);
        return StringUtils.hasText(key) && key.startsWith(directory + "/");
    }

    public String resolvePublicUrl(String rawUrlOrKey) {
        if (!StringUtils.hasText(rawUrlOrKey)) {
            return null;
        }

        String key = extractStorageKey(rawUrlOrKey);
        if (!StringUtils.hasText(key)) {
            return rawUrlOrKey;
        }

        return buildPublicPrefix() + key;
    }

    private String buildS3Prefix() {
        return String.format("https://%s.s3.%s.amazonaws.com/", bucket, region);
    }

    private String buildPublicPrefix() {
        if (!StringUtils.hasText(publicBaseUrl)) {
            return buildS3Prefix();
        }

        String normalized = publicBaseUrl.trim().replaceAll("/+$", "");
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://" + normalized;
        }
        return normalized + "/";
    }

    private String sanitizeKey(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }

        String sanitized = key.trim().replaceFirst("^/+", "");
        return sanitized.isBlank() ? null : sanitized;
    }

    private void validateExtension(String extension) {
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new CustomException(ErrorCode.INVALID_FILE_EXTENSION);
        }
    }

    private void validateDirectory(String directory) {
        if (!ALLOWED_DIRECTORIES.contains(directory)) {
            throw new CustomException(ErrorCode.INVALID_DIRECTORY);
        }
    }

    private String getContentType(String extension) {
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "heic" -> "image/heic";
            case "mp4" -> "video/mp4";
            case "mov" -> "video/quicktime";
            case "mp3" -> "audio/mpeg";
            case "m4a" -> "audio/mp4";
            case "wav" -> "audio/wav";
            case "pdf" -> "application/pdf";
            default -> throw new CustomException(ErrorCode.INVALID_FILE_EXTENSION);
        };
    }
}
