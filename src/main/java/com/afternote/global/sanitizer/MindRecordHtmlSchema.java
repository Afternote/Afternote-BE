package com.afternote.global.sanitizer;

/**
 * Swagger 등 API 문서용 — 마인드 레코드 본문은 HTML 조각으로 통일한다.
 */
public final class MindRecordHtmlSchema {

    public static final String CONTENT =
            "본문 HTML 조각(UTF-8). WebView 등에 그대로 렌더링 가능한 수준의 태그·스타일이며, "
                    + "저장 시 서버에서 XSS 완화를 위해 sanitize 된다.";

    public static final String CONTENT_EXAMPLE =
            "<p style=\"text-align:center\"><strong>오늘</strong>은 좋은 날이에요.</p>";

    private MindRecordHtmlSchema() {
    }
}
