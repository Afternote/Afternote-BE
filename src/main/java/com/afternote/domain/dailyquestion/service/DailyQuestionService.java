package com.afternote.domain.dailyquestion.service;

import com.afternote.domain.dailyquestion.dto.DailyQuestionHealthResponse;
import org.springframework.stereotype.Service;

@Service
public class DailyQuestionService {

    public DailyQuestionHealthResponse getLayerStatus() {
        return new DailyQuestionHealthResponse("dailyquestion mvc scaffold ready");
    }
}
