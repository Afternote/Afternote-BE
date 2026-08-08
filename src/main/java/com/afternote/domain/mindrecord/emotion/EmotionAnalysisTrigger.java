package com.afternote.domain.mindrecord.emotion;

import com.afternote.domain.diary.model.TodayMood;

import java.util.Objects;

/**
 * 마인드레코드 감정 분석 이벤트 발행 여부 판단.
 * 분석 입력(제목/본문/기분 등)이 실질적으로 바뀔 때만 재분석한다.
 */
public final class EmotionAnalysisTrigger {

    private EmotionAnalysisTrigger() {
    }

    public static boolean shouldAnalyzeDiary(
            boolean wasDraft,
            boolean isFinal,
            String beforeTitle,
            String beforeContent,
            TodayMood beforeMood,
            String afterTitle,
            String afterContent,
            TodayMood afterMood
    ) {
        if (!isFinal) {
            return false;
        }
        if (wasDraft) {
            return true;
        }
        return !Objects.equals(nullToEmpty(beforeTitle), nullToEmpty(afterTitle))
                || !Objects.equals(nullToEmpty(beforeContent), nullToEmpty(afterContent))
                || !Objects.equals(beforeMood, afterMood);
    }

    public static boolean shouldAnalyzeDeepThought(
            boolean wasDraft,
            boolean isFinal,
            String beforeTitle,
            String beforeContent,
            String afterTitle,
            String afterContent
    ) {
        if (!isFinal) {
            return false;
        }
        if (wasDraft) {
            return true;
        }
        return !Objects.equals(nullToEmpty(beforeTitle), nullToEmpty(afterTitle))
                || !Objects.equals(nullToEmpty(beforeContent), nullToEmpty(afterContent));
    }

    public static boolean shouldAnalyzeDailyQuestion(
            boolean wasDraft,
            boolean isFinal,
            String beforeContent,
            String afterContent
    ) {
        if (!isFinal) {
            return false;
        }
        if (wasDraft) {
            return true;
        }
        return !Objects.equals(nullToEmpty(beforeContent), nullToEmpty(afterContent));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
