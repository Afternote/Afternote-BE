package com.afternote.domain.image.service;

import com.afternote.domain.image.dto.PresignedUrlResponse;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    static final String STAGING_SEGMENT = "staging";
    static final String PERMANENT_SEGMENT = "permanent";
    static final String RECEIVER_OWNER = "receiver";

    private static final Pattern SRC_OR_HREF =
            Pattern.compile("(?:src|href)\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.s3.region}")
    private String region;

    @Value("${cloud.aws.public-base-url:}")
    private String publicBaseUrl;

    /** 이미지 업로드 상한(바이트). 정책 미확정 임시값 — 설정으로 조정. */
    @Value("${cloud.aws.s3.upload.max-bytes.image:10485760}")
    private long maxImageBytes;

    /** 영상 업로드 상한(바이트). 정책 미확정 임시값 — 설정으로 조정. */
    @Value("${cloud.aws.s3.upload.max-bytes.video:209715200}")
    private long maxVideoBytes;

    /** 음성 업로드 상한(바이트). 정책 미확정 임시값 — 설정으로 조정. */
    @Value("${cloud.aws.s3.upload.max-bytes.audio:52428800}")
    private long maxAudioBytes;

    /** 문서(PDF) 업로드 상한(바이트). 정책 미확정 임시값 — 설정으로 조정. */
    @Value("${cloud.aws.s3.upload.max-bytes.document:20971520}")
    private long maxDocumentBytes;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "heic",
            "mp4", "mov",
            "mp3", "m4a", "wav",
            "pdf"
    );
    private static final Set<String> ALLOWED_DIRECTORIES = Set.of(
            "profiles", "timeletters", "afternotes", "mindrecords", "documents"
    );
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "heic"
    );
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "mov");
    private static final Set<String> AUDIO_EXTENSIONS = Set.of("mp3", "m4a", "wav");
    private static final Duration PRESIGNED_URL_EXPIRATION = Duration.ofMinutes(10);

    /**
     * @deprecated {@link #generatePresignedUrl(String, String, Long, Long)} 사용. owner 미지정 시 {@code receiver}.
     */
    @Deprecated
    public PresignedUrlResponse generatePresignedUrl(String directory, String extension, Long contentLength) {
        return generatePresignedUrl(directory, extension, contentLength, null);
    }

    /**
     * 업로드용 presigned URL. 객체는 {@code {directory}/staging/{owner}/{uuid}.{ext}} 에 생성된다.
     * {@code contentLength} 는 서명에 포함되므로 PUT 시 동일 Content-Length 헤더가 필수다.
     */
    public PresignedUrlResponse generatePresignedUrl(String directory, String extension, Long contentLength, Long userId) {
        String normalizedDir = directory.toLowerCase();
        String normalizedExt = extension.toLowerCase().replaceFirst("^\\.", "");
        String owner = resolveOwnerSegment(userId);

        validateDirectory(normalizedDir);
        validateExtension(normalizedExt);
        long maxContentLength = resolveMaxContentLength(normalizedExt);
        validateContentLength(contentLength, maxContentLength);

        String fileName = UUID.randomUUID() + "." + normalizedExt;
        String key = buildStagingKey(normalizedDir, owner, fileName);
        String contentType = getContentType(normalizedExt);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
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
                    .contentLength(contentLength)
                    .maxContentLength(maxContentLength)
                    .build();
        } catch (Exception e) {
            log.error("Presigned URL generation failed for key: {}", key, e);
            throw new CustomException(ErrorCode.PRESIGNED_URL_GENERATION_FAILED);
        }
    }

    /**
     * staging/legacy 미디어를 permanent로 승격하고 storage key를 반환한다.
     * 관리되지 않는 URL이면 기존처럼 extract 결과(또는 원문)를 반환한다.
     */
    public String promoteMediaKey(String directory, Long userId, String rawUrlOrKey) {
        if (!StringUtils.hasText(rawUrlOrKey)) {
            return rawUrlOrKey;
        }

        String key = extractStorageKey(rawUrlOrKey);
        if (!StringUtils.hasText(key)) {
            return rawUrlOrKey;
        }

        String normalizedDir = directory.toLowerCase();
        String owner = resolveOwnerSegment(userId);
        if (!belongsToDirectory(key, normalizedDir)) {
            return key;
        }
        if (!isPromotableKey(key, normalizedDir, owner)) {
            return key;
        }
        return promoteToPermanent(normalizedDir, owner, key);
    }

    /**
     * HTML 본문에서 관리되는 staging/legacy 미디어 URL을 permanent로 승격하고 치환한다.
     */
    public String promoteReferencedMediaInHtml(String directory, Long userId, String html) {
        if (!StringUtils.hasText(html)) {
            return html;
        }
        String owner = resolveOwnerSegment(userId);
        String normalizedDir = directory.toLowerCase();
        Map<String, String> replacements = new LinkedHashMap<>();

        Matcher matcher = SRC_OR_HREF.matcher(html);
        while (matcher.find()) {
            String rawReference = matcher.group(1);
            String key = extractStorageKey(rawReference);
            if (!StringUtils.hasText(key) || !belongsToDirectory(key, normalizedDir)) {
                continue;
            }
            if (!isPromotableKey(key, normalizedDir, owner)) {
                continue;
            }
            String permanentKey = promoteToPermanent(normalizedDir, owner, key);
            registerReplacementVariants(replacements, key, permanentKey);
        }

        String result = html;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
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
        return StringUtils.hasText(key) && belongsToDirectory(key, directory.toLowerCase());
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

    private String promoteToPermanent(String directory, String owner, String sourceKey) {
        if (isPermanentKey(sourceKey, directory, owner)) {
            return sourceKey;
        }

        String fileName = extractFileName(sourceKey, directory, owner);
        String permanentKey = buildPermanentKey(directory, owner, fileName);

        try {
            s3Client.copyObject(CopyObjectRequest.builder()
                    .sourceBucket(bucket)
                    .sourceKey(sourceKey)
                    .destinationBucket(bucket)
                    .destinationKey(permanentKey)
                    .build());
            log.debug("Promoted S3 object {} -> {}", sourceKey, permanentKey);
        } catch (Exception e) {
            log.error("S3 promote failed sourceKey={} permanentKey={}", sourceKey, permanentKey, e);
            throw new CustomException(ErrorCode.PRESIGNED_URL_GENERATION_FAILED);
        }

        if (isStagingKey(sourceKey, directory, owner)) {
            deleteStagingOriginal(sourceKey);
        }

        return permanentKey;
    }

    private void deleteStagingOriginal(String sourceKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(sourceKey)
                    .build());
            log.debug("Deleted staging original {}", sourceKey);
        } catch (Exception e) {
            // permanent 사본은 이미 있으므로 승격 자체는 성공으로 두고, lifecycle 이 후속 정리한다.
            log.warn("Failed to delete staging original after promote: {}", sourceKey, e);
        }
    }

    private void registerReplacementVariants(Map<String, String> replacements, String sourceKey, String permanentKey) {
        String permanentUrl = resolvePublicUrl(permanentKey);
        replacements.putIfAbsent(sourceKey, permanentUrl);
        replacements.putIfAbsent(resolvePublicUrl(sourceKey), permanentUrl);
        replacements.putIfAbsent(buildS3Prefix() + sourceKey, permanentUrl);
        if (StringUtils.hasText(publicBaseUrl)) {
            replacements.putIfAbsent(buildPublicPrefix() + sourceKey, permanentUrl);
        }
    }

    private boolean isPromotableKey(String key, String directory, String owner) {
        if (isPermanentKey(key, directory, owner)) {
            return false;
        }
        if (isStagingKey(key, directory, owner)) {
            return true;
        }
        return isLegacyKey(key, directory);
    }

    private boolean isStagingKey(String key, String directory, String owner) {
        return key.startsWith(directory + "/" + STAGING_SEGMENT + "/" + owner + "/");
    }

    private boolean isPermanentKey(String key, String directory, String owner) {
        return key.startsWith(directory + "/" + PERMANENT_SEGMENT + "/" + owner + "/");
    }

    private boolean isLegacyKey(String key, String directory) {
        if (!key.startsWith(directory + "/")) {
            return false;
        }
        String remainder = key.substring(directory.length() + 1);
        return !remainder.startsWith(STAGING_SEGMENT + "/") && !remainder.startsWith(PERMANENT_SEGMENT + "/");
    }

    private boolean belongsToDirectory(String key, String directory) {
        return key.startsWith(directory + "/");
    }

    private String extractFileName(String key, String directory, String owner) {
        if (isStagingKey(key, directory, owner)) {
            return key.substring((directory + "/" + STAGING_SEGMENT + "/" + owner + "/").length());
        }
        if (isLegacyKey(key, directory)) {
            return key.substring(directory.length() + 1);
        }
        return key.substring(key.lastIndexOf('/') + 1);
    }

    static String buildStagingKey(String directory, String owner, String fileName) {
        return directory + "/" + STAGING_SEGMENT + "/" + owner + "/" + fileName;
    }

    static String buildPermanentKey(String directory, String owner, String fileName) {
        return directory + "/" + PERMANENT_SEGMENT + "/" + owner + "/" + fileName;
    }

    static String resolveOwnerSegment(Long userId) {
        return userId != null ? String.valueOf(userId) : RECEIVER_OWNER;
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

    private void validateContentLength(Long contentLength, long maxContentLength) {
        if (contentLength == null || contentLength <= 0) {
            throw new CustomException(ErrorCode.INVALID_FILE_SIZE);
        }
        if (contentLength > maxContentLength) {
            throw new CustomException(ErrorCode.FILE_SIZE_EXCEEDED);
        }
    }

    long resolveMaxContentLength(String extension) {
        if (IMAGE_EXTENSIONS.contains(extension)) {
            return maxImageBytes;
        }
        if (VIDEO_EXTENSIONS.contains(extension)) {
            return maxVideoBytes;
        }
        if (AUDIO_EXTENSIONS.contains(extension)) {
            return maxAudioBytes;
        }
        if ("pdf".equals(extension)) {
            return maxDocumentBytes;
        }
        throw new CustomException(ErrorCode.INVALID_FILE_EXTENSION);
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
