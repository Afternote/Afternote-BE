package com.afternote.domain.diary.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Schema(description = "다이어리 목록 응답")
@Getter
@Builder
public class DiaryListResponse {

    @Schema(description = "다이어리 목록")
    private List<DiaryResponse> diaries;

    public static DiaryListResponse from(List<DiaryResponse> diaries) {
        return DiaryListResponse.builder()
                .diaries(diaries)
                .build();
    }
}
