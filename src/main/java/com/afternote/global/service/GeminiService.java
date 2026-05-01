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

   
}