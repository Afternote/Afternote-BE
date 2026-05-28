package com.afternote.domain.receiver.dto;

import com.afternote.domain.diary.dto.DiaryResponse;

import java.util.List;

public record ReceivedDiaryListResponse(
        List<DiaryResponse> diaries
) {
    public static ReceivedDiaryListResponse from(List<DiaryResponse> diaries) {
        return new ReceivedDiaryListResponse(diaries);
    }
}