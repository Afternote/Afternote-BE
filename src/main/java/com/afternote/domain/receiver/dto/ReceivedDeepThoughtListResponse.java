package com.afternote.domain.receiver.dto;

import com.afternote.domain.deepthought.dto.DeepThoughtResponse;
import com.afternote.domain.deepthought.dto.DeepThoughtTagCountResponse;

import java.util.List;

public record ReceivedDeepThoughtListResponse(
        List<String> categories,
        List<DeepThoughtTagCountResponse> tagCounts,
        List<DeepThoughtResponse> deepThoughts
) {
    public static ReceivedDeepThoughtListResponse from(
            List<String> categories,
            List<DeepThoughtTagCountResponse> tagCounts,
            List<DeepThoughtResponse> deepThoughts
    ) {
        return new ReceivedDeepThoughtListResponse(categories, tagCounts, deepThoughts);
    }
}