package com.afternote.domain.mindrecord.emotion;

import java.util.Optional;
import java.util.Set;

/**
 * Gemini 감정 분류 허용 목록. 프롬프트와 동일한 8개만 저장한다.
 */
public final class EmotionCategoryAllowlist {

    public static final Set<String> ALLOWED = Set.of(
            "기쁨",
            "평온",
            "슬픔",
            "우울",
            "분노",
            "불안",
            "놀람",
            "감사"
    );

    private EmotionCategoryAllowlist() {
    }

    /**
     * 허용 목록에 있으면 정규화된 카테고리를 반환한다. 없으면 empty.
     */
    public static Optional<String> normalizeIfAllowed(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String s = raw.trim().replaceAll("\\s+", " ");
        if (s.startsWith("- ")) {
            s = s.substring(2).trim();
        }
        // 문장·따옴표 등으로 감싸진 경우 목록 단어만 정확히 일치할 때만 통과
        if (ALLOWED.contains(s)) {
            return Optional.of(s);
        }
        return Optional.empty();
    }

    public static boolean isAllowed(String raw) {
        return normalizeIfAllowed(raw).isPresent();
    }
}
