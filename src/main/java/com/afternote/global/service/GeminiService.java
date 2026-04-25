package com.afternote.global.service;

import com.afternote.domain.mindrecord.emotion.model.Emotion;
import com.afternote.domain.mindrecord.emotion.repository.EmotionRepository;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.afternote.domain.mindrecord.emotion.service.EmotionCacheService;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService {

    private final ChatModel chatModel;
    private final EmotionRepository emotionRepository;
    private final UserRepository userRepository;
    private final EmotionCacheService emotionCacheService;

    @Transactional
    public String summaryEmotion(Long userId){
        userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        
        // 1) Redis에서 먼저 조회
        String cachedSummary = emotionCacheService.getEmotionSummaryText(userId);
        if (cachedSummary != null && !cachedSummary.isEmpty()) {
            return cachedSummary;
        }
        
        // 2) Redis에 없으면 LLM으로 생성
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);

        String keywordsString = emotionRepository.findByUserIdAndCreatedAtAfter(userId,sevenDaysAgo).stream()
            .map(Emotion::getEmotionCategory)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));

        String instruction = String.format(
                "사용자의 이번 주 일기에서 가장 많이 등장한 핵심 키워드는 [%s]입니다.\n" +
                        "이 키워드들을 조합하여 사용자의 한 주를 아우르는 통찰력 있는 '한 문장'을 작성해주세요.\n\n" +
                        "[제약 조건]\n" +
                        "1. 말투: 따뜻하고 부드러운 존댓말 (예: '~마음이 엿보입니다', '~시간이었군요')\n" +
                        "2. 문장은 자연스럽게 연결하고, 모든 키워드를 다 쓸 필요는 없음 (가장 중요한 2개 정도 활용)\n" +
                        "3. 길이: 20자 이내\n\n" +
                        "결과만 출력하세요.",
                keywordsString
        );

        String answer = "";
        try {
            answer = chatModel.call(new Prompt(instruction)).getResult().getOutput().getText().trim();
            log.debug("제미니 요약 성공!!!: {}",answer);
        } catch (Exception e) {
            log.error("Gemini API 호출 실패, 기본값 반환: {}", e.getMessage());
            throw new CustomException(ErrorCode.GEMINI_FAILED);
        }
        
        // 3) 생성한 요약을 Redis에 저장 (1일)
        if (!answer.isEmpty()) {
            emotionCacheService.saveSummaryText(userId, answer);
        }
        
        return answer;
    }
}