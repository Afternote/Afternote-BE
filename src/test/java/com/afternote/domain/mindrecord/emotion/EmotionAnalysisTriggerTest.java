package com.afternote.domain.mindrecord.emotion;

import com.afternote.domain.diary.model.TodayMood;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmotionAnalysisTriggerTest {

    @Test
    @DisplayName("일기 - 수신자만 바뀌는 경우(입력 동일)면 재분석 안 함")
    void diary_sameInput_noAnalyze() {
        assertThat(EmotionAnalysisTrigger.shouldAnalyzeDiary(
                false, true,
                "t", "c", TodayMood.SAD,
                "t", "c", TodayMood.SAD
        )).isFalse();
    }

    @Test
    @DisplayName("일기 - 본문/기분 변경 시 재분석")
    void diary_contentChanged_analyze() {
        assertThat(EmotionAnalysisTrigger.shouldAnalyzeDiary(
                false, true,
                "t", "c", TodayMood.SAD,
                "t", "c2", TodayMood.SAD
        )).isTrue();
        assertThat(EmotionAnalysisTrigger.shouldAnalyzeDiary(
                false, true,
                "t", "c", TodayMood.SAD,
                "t", "c", TodayMood.HAPPY
        )).isTrue();
    }

    @Test
    @DisplayName("일기 - draft→final 이면 재분석")
    void diary_draftToFinal_analyze() {
        assertThat(EmotionAnalysisTrigger.shouldAnalyzeDiary(
                true, true,
                "t", "c", TodayMood.SAD,
                "t", "c", TodayMood.SAD
        )).isTrue();
    }

    @Test
    @DisplayName("데일리질문 - 동일 content면 재분석 안 함")
    void dailyQuestion_sameContent_noAnalyze() {
        assertThat(EmotionAnalysisTrigger.shouldAnalyzeDailyQuestion(
                false, true, "answer", "answer"
        )).isFalse();
    }

    @Test
    @DisplayName("깊은생각 - 제목만 변경 시 재분석")
    void deepThought_titleChanged_analyze() {
        assertThat(EmotionAnalysisTrigger.shouldAnalyzeDeepThought(
                false, true, "a", "body", "b", "body"
        )).isTrue();
    }
}
