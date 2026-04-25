package com.afternote.domain.deepthought.controller;

import com.afternote.domain.deepthought.dto.DeepThoughtCategoryCreateRequest;
import com.afternote.domain.deepthought.dto.DeepThoughtCategoryListResponse;
import com.afternote.domain.deepthought.dto.DeepThoughtCategoryResponse;
import com.afternote.domain.deepthought.dto.DeepThoughtCategoryUpdateRequest;
import com.afternote.domain.deepthought.dto.DeepThoughtCreateRequest;
import com.afternote.domain.deepthought.dto.DeepThoughtListResponse;
import com.afternote.domain.deepthought.dto.DeepThoughtResponse;
import com.afternote.domain.deepthought.service.DeepThoughtCategoryService;
import com.afternote.domain.deepthought.service.DeepThoughtService;
import com.afternote.global.common.ApiResponse;
import com.afternote.global.resolver.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "🤔 DeepThought API", description = "깊은 생각 API")
@RestController
@RequestMapping("/api/v1/deep-thought")
@RequiredArgsConstructor
public class DeepThoughtController {

    private final DeepThoughtService deepThoughtService;
    private final DeepThoughtCategoryService deepThoughtCategoryService;

    @Operation(summary = "깊은 생각 작성", description = "깊은 생각을 작성합니다.")
    @PostMapping
    public ApiResponse<DeepThoughtResponse> createDeepThought(
            @Parameter(hidden = true) @UserId Long userId,
            @Valid @RequestBody DeepThoughtCreateRequest request
    ) {
        return ApiResponse.success(deepThoughtService.createDeepThought(userId, request));
    }

    @Operation(summary = "깊은 생각 조회", description = "날짜/태그/카테고리 조건으로 깊은 생각을 조회합니다.")
    @GetMapping
    public ApiResponse<DeepThoughtListResponse> getDeepThoughts(
            @Parameter(hidden = true) @UserId Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String category
    ) {
        return ApiResponse.success(deepThoughtService.getDeepThoughts(userId, date, tag, category));
    }

    @Operation(summary = "깊은 생각 랜덤 조회", description = "유저의 깊은 생각 중 하나를 랜덤으로 조회합니다.")
    @GetMapping("/random")
    public ApiResponse<DeepThoughtResponse> getRandomDeepThought(
            @Parameter(hidden = true) @UserId Long userId
    ) {
        return ApiResponse.success(deepThoughtService.getRandomDeepThought(userId));
    }

    @Operation(summary = "깊은 생각 삭제", description = "깊은 생각을 삭제합니다.")
    @DeleteMapping("/{deepThoughtId}")
    public ApiResponse<Void> deleteDeepThought(
            @Parameter(hidden = true) @UserId Long userId,
            @PathVariable Long deepThoughtId
    ) {
        deepThoughtService.deleteDeepThought(userId, deepThoughtId);
        return ApiResponse.success(null);
    }

    @Operation(summary = "카테고리 조회", description = "유저의 깊은 생각 카테고리 목록을 조회합니다.")
    @GetMapping("/categories")
    public ApiResponse<DeepThoughtCategoryListResponse> getCategories(
            @Parameter(hidden = true) @UserId Long userId
    ) {
        return ApiResponse.success(deepThoughtCategoryService.getCategories(userId));
    }

    @Operation(summary = "카테고리 추가", description = "유저 카테고리를 추가합니다.")
    @PostMapping("/categories")
    public ApiResponse<DeepThoughtCategoryResponse> addCategory(
            @Parameter(hidden = true) @UserId Long userId,
            @Valid @RequestBody DeepThoughtCategoryCreateRequest request
    ) {
        return ApiResponse.success(deepThoughtCategoryService.addCategory(userId, request));
    }

    @Operation(summary = "카테고리 수정", description = "유저 카테고리를 수정합니다.")
    @PatchMapping("/categories/{categoryId}")
    public ApiResponse<DeepThoughtCategoryResponse> updateCategory(
            @Parameter(hidden = true) @UserId Long userId,
            @PathVariable Long categoryId,
            @Valid @RequestBody DeepThoughtCategoryUpdateRequest request
    ) {
        return ApiResponse.success(deepThoughtCategoryService.updateCategory(userId, categoryId, request));
    }

    @Operation(summary = "카테고리 삭제", description = "유저 카테고리를 삭제합니다.")
    @DeleteMapping("/categories/{categoryId}")
    public ApiResponse<Void> deleteCategory(
            @Parameter(hidden = true) @UserId Long userId,
            @PathVariable Long categoryId
    ) {
        deepThoughtCategoryService.deleteCategory(userId, categoryId);
        return ApiResponse.success(null);
    }

}
