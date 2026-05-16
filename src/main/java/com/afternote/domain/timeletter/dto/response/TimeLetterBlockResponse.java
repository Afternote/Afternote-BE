package com.afternote.domain.timeletter.dto.response;

import com.afternote.domain.timeletter.model.TimeLetterBlock;
import com.afternote.domain.timeletter.model.TimeLetterBlockType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.function.Function;

@Getter
@Builder
@Schema(description = "타임레터 본문 블록 응답")
public class TimeLetterBlockResponse {

    private Long id;
    private TimeLetterBlockType blockType;
    private Integer blockOrder;
    private String textContent;
    private String url;
    private String mimeType;

    public static TimeLetterBlockResponse from(
            TimeLetterBlock block,
            Function<String, String> presignedUrlGenerator
    ) {
        String responseUrl = block.getUrl();

        if (block.getBlockType() != TimeLetterBlockType.TEXT
                && block.getBlockType() != TimeLetterBlockType.LINK
                && block.getUrl() != null
                && !block.getUrl().isBlank()) {
            responseUrl = presignedUrlGenerator.apply(block.getUrl());
        }

        return TimeLetterBlockResponse.builder()
                .id(block.getId())
                .blockType(block.getBlockType())
                .blockOrder(block.getBlockOrder())
                .textContent(block.getTextContent())
                .url(responseUrl)
                .mimeType(block.getMimeType())
                .build();
    }
}