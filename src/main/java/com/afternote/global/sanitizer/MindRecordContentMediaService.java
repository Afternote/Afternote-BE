package com.afternote.global.sanitizer;

import com.afternote.domain.image.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 마인드레코드 본문 HTML: XSS sanitize 후 staging 미디어를 permanent로 승격한다.
 */
@Service
@RequiredArgsConstructor
public class MindRecordContentMediaService {

    private static final String MINDRECORDS_DIRECTORY = "mindrecords";

    private final MindRecordHtmlSanitizer mindRecordHtmlSanitizer;
    private final S3Service s3Service;

    public String prepareContentForSave(Long userId, String rawContent) {
        if (rawContent == null) {
            return null;
        }
        String sanitized = mindRecordHtmlSanitizer.sanitize(rawContent);
        return s3Service.promoteReferencedMediaInHtml(MINDRECORDS_DIRECTORY, userId, sanitized);
    }
}
