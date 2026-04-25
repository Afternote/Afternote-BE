package com.afternote.domain.deepthought.service;

import com.afternote.domain.deepthought.dto.DeepThoughtCategoryListResponse;
import com.afternote.domain.deepthought.dto.DeepThoughtCategoryCreateRequest;
import com.afternote.domain.deepthought.dto.DeepThoughtCategoryResponse;
import com.afternote.domain.deepthought.dto.DeepThoughtCategoryUpdateRequest;
import com.afternote.domain.deepthought.model.DeepThoughtCategory;
import com.afternote.domain.deepthought.repository.DeepThoughtCategoryRepository;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeepThoughtCategoryService {
    private final DeepThoughtCategoryRepository deepThoughtCategoryRepository;
    private final UserRepository userRepository;

    public DeepThoughtCategoryListResponse getCategories(Long userId) {
        List<DeepThoughtCategoryResponse> categories = deepThoughtCategoryRepository
                .findByUserIdOrderByIdAsc(userId)
                .stream()
                .map(DeepThoughtCategoryResponse::from)
                .toList();
        return DeepThoughtCategoryListResponse.from(categories);
    }

    @Transactional
    public DeepThoughtCategoryResponse addCategory(Long userId, DeepThoughtCategoryCreateRequest request) {
        String normalizedTitle = request.getTitle().trim();
        if (deepThoughtCategoryRepository.existsByUserIdAndTitle(userId, normalizedTitle)) {
            throw new CustomException(ErrorCode.CATEGORY_REQUIRED);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        DeepThoughtCategory category = DeepThoughtCategory.create(user, normalizedTitle);
        return DeepThoughtCategoryResponse.from(deepThoughtCategoryRepository.save(category));
    }

    @Transactional
    public DeepThoughtCategoryResponse updateCategory(Long userId, Long categoryId, DeepThoughtCategoryUpdateRequest request) {
        DeepThoughtCategory category = deepThoughtCategoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_REQUIRED));

        category.updateTitle(request.getTitle().trim());
        return DeepThoughtCategoryResponse.from(category);
    }

    @Transactional
    public void deleteCategory(Long userId, Long categoryId) {
        DeepThoughtCategory category = deepThoughtCategoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_REQUIRED));
        deepThoughtCategoryRepository.delete(category);
    }
}
