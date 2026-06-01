package com.afternote.global.sanitizer;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 마인드 레코드 본문(UTF-8 HTML 조각). 프론트 계약:
 * <ul>
 *   <li>정렬: {@code <div style="text-align:left|center|right">} 또는 {@code <p align="left|center|right|justify">}</li>
 *   <li>제목 / 머릿말 / 부머릿말 / 본문: {@code h1}, {@code h2}, {@code h3}, {@code p}</li>
 *   <li>이탤릭: {@code em}, {@code i} / 밑줄: {@code u} / 취소선: {@code s}, {@code del}</li>
 *   <li>링크: {@code <a href="https://...">} (선택 {@code target="_blank"})</li>
 *   <li>이미지: {@code <img src="https://..." alt="..." width="..." height="..." style="...">}</li>
 * </ul>
 * {@code div}·줄바꿈·굵게 등은 편집기에서 쓰일 수 있어 제한적으로 허용한다.
 */
@Component
public class MindRecordHtmlSanitizer {

    /** {@code align} 속성 값 (대소문자 무시). */
    private static final Pattern ALIGN_ATTR = Pattern.compile("(?i)(left|center|right|justify)");

    private static final Pattern TARGET_BLANK = Pattern.compile("(?i)_blank");

    private static final PolicyFactory POLICY =
            Sanitizers.LINKS
                    .and(Sanitizers.IMAGES)
                    .and(new HtmlPolicyBuilder()
                            .allowElements(
                                    "h1", "h2", "h3", "p", "div",
                                    "em", "i", "u", "s", "del",
                                    "br", "strong", "b", "span",
                                    "a", "img"
                            )
                            .allowAttributes("align")
                            .matching(ALIGN_ATTR)
                            .onElements("p", "div", "h1", "h2", "h3")
                            .allowAttributes("target")
                            .matching(TARGET_BLANK)
                            .onElements("a")
                            .allowStyling()
                            .toFactory());

    public String sanitize(String html) {
        if (html == null) {
            return null;
        }
        return POLICY.sanitize(html);
    }
}
