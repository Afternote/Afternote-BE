package com.afternote.global.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;


@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService {

    private final ChatModel chatModel;

    /**
     * 일기 제목·내용·오늘의 기분을 바탕으로 짧은 감정 키워드(한 줄)를 반환한다. 실패 시 null.
     */
    public String analyzeEmotionFromDiary(String title, String content, String todayMoodLabel) {
        String ctx = String.format(
                "일기 제목: %s\n일기 내용: %s\n오늘의 기분: %s",
                blankToEmpty(title),
                blankToEmpty(content),
                todayMoodLabel != null ? todayMoodLabel : "없음"
        );
        return analyzeEmotionShort(ctx, "일기");
    }

    /**
     * 데일리 질문과 답을 바탕으로 짧은 감정 키워드(한 줄)를 반환한다. 실패 시 null.
     */
    public String analyzeEmotionFromDailyQuestion(String question, String answer) {
        String ctx = String.format(
                "질문: %s\n답변: %s",
                blankToEmpty(question),
                blankToEmpty(answer)
        );
        return analyzeEmotionShort(ctx, "데일리 질문");
    }

    /**
     * 깊은 생각 제목·내용을 바탕으로 짧은 감정 키워드(한 줄)를 반환한다. 실패 시 null.
     */
    public String analyzeEmotionFromDeepThought(String title, String content) {
        String ctx = String.format(
                "제목: %s\n내용: %s",
                blankToEmpty(title),
                blankToEmpty(content)
        );
        return analyzeEmotionShort(ctx, "깊은 생각");
    }

    private String analyzeEmotionShort(String contextBlock, String recordTypeLabel) {
        String instruction = String.format(
                "아래는 사용자의 %s 기록이다. 내용만 보고 가장 잘 맞는 감정을 **아래 목록에서 정확히 하나만** 고른다.\n"
                        + "[감정 목록 — 아래 단어 중 하나만 그대로 출력]\n"
                        + "- 기쁨\n"
                        + "- 평온\n"
                        + "- 슬픔\n"
                        + "- 우울\n"
                        + "- 분노\n"
                        + "- 불안\n"
                        + "- 놀람\n"
                        + "- 감사\n"
                        + "[출력 규칙] 위 목록의 단어 하나만 출력. 하이픈(-)·따옴표·설명·다른 문장 금지.\n\n"
                        + "%s",
                recordTypeLabel,
                contextBlock
        );
        try {
            String text = chatModel.call(new Prompt(instruction)).getResult().getOutput().getText().trim();
            if (text.startsWith("- ")) {
                text = text.substring(2).trim();
            }
            return text;
        } catch (Exception e) {
            log.error("Gemini 감정 분석 실패 ({})", recordTypeLabel, e);
            return null;
        }
    }

    private static String blankToEmpty(String s) {
        return s != null ? s : "";
    }

    /**
     * 주간 감정 키워드 JSON을 바탕으로 인사이트 문구(한국어)를 생성한다. 실패 시 null.
     */
    public String generateWeeklyMindRecordSummary(String keywordJson) {
        String instruction = String.format(
                "아래 JSON은 한 사용자의 한 주 동안 기록에서 집계된 감정 키워드와 비율(퍼센트)이다.\n"
                        + "%s\n\n"
                        + "[작성 지침]\n"
                        + "- 한국어로, 위 키워드를 자연스럽게 녹여 반성·격려가 담긴 인사이트를 약 2문장으로 쓴다.\n"
                        + "- 이어서 짧은 격려 한 줄과 이모지 하나를 덧붙인다.\n"
                        + "- JSON·목록·따옴표로 감싼 메타 설명 없이 본문만 출력한다.\n"
                        + "- 키워드가 비어 있거나 비율이 모두 0에 가깝다면, 기록 습관을 칭찬하는 따뜻한 한두 문장으로 대체한다.",
                blankToEmpty(keywordJson)
        );
        try {
            log.info("Gemini 주간 마음기록 요약 요청: {}", instruction);
            return chatModel.call(new Prompt(instruction)).getResult().getOutput().getText().trim();
        } catch (Exception e) {
            log.error("Gemini 주간 마음기록 요약 실패", e);
            return null;
        }
    }
}