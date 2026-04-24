package com.afternote.domain.diary.service;

import com.afternote.domain.diary.dto.DiaryHealthResponse;
import org.springframework.stereotype.Service;

@Service
public class DiaryService {

    public DiaryHealthResponse getLayerStatus() {
        return new DiaryHealthResponse("diary mvc scaffold ready");
    }
}
