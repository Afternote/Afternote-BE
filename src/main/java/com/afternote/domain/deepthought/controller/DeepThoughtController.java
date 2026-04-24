package com.afternote.domain.deepthought.controller;

import com.afternote.domain.deepthought.dto.DeepThoughtHealthResponse;
import com.afternote.domain.deepthought.service.DeepThoughtService;
import com.afternote.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "🤔 DeepThought API", description = "깊은 생각 API")
@RestController
@RequestMapping("/api/v1/deep-thoughts")
@RequiredArgsConstructor
public class DeepThoughtController {

    private final DeepThoughtService deepThoughtService;

    @Operation(summary = "DeepThought MVC 레이어 상태 확인", description = "DeepThought 도메인의 MVC 스캐폴딩 상태를 확인합니다.")
    @GetMapping("/health")
    public ApiResponse<DeepThoughtHealthResponse> getLayerStatus() {
        return ApiResponse.success(deepThoughtService.getLayerStatus());
    }
}
