package com.afternote.global.sanitizer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MindRecordHtmlSanitizerTest {

    private final MindRecordHtmlSanitizer sanitizer = new MindRecordHtmlSanitizer();

    @Test
    @DisplayName("링크(a) 태그를 허용한다")
    void allowsAnchorTag() {
        String input = "<p><a href=\"https://example.com/path\">더 보기</a></p>";

        String sanitized = sanitizer.sanitize(input);

        assertThat(sanitized).contains("<a href=\"https://example.com/path\"");
        assertThat(sanitized).contains("더 보기</a>");
    }

    @Test
    @DisplayName("이미지(img) 태그를 허용한다")
    void allowsImageTag() {
        String input = "<p><img src=\"https://cdn.example.com/photo.png\" alt=\"기록\" width=\"320\" /></p>";

        String sanitized = sanitizer.sanitize(input);

        assertThat(sanitized).contains("<img");
        assertThat(sanitized).contains("src=\"https://cdn.example.com/photo.png\"");
        assertThat(sanitized).contains("alt=\"기록\"");
        assertThat(sanitized).contains("width=\"320\"");
    }

    @Test
    @DisplayName("javascript 링크는 제거한다")
    void stripsUnsafeLinkProtocol() {
        String input = "<a href=\"javascript:alert(1)\">위험</a>";

        String sanitized = sanitizer.sanitize(input);

        assertThat(sanitized).doesNotContain("javascript:");
        assertThat(sanitized).doesNotContain("<a ");
    }

    @Test
    @DisplayName("target=_blank 링크를 허용한다")
    void allowsBlankTargetOnLinks() {
        String input = "<a href=\"https://example.com\" target=\"_blank\">외부 링크</a>";

        String sanitized = sanitizer.sanitize(input);

        assertThat(sanitized).contains("target=\"_blank\"");
        assertThat(sanitized).contains("href=\"https://example.com\"");
    }
}
