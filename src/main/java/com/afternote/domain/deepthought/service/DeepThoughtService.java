package com.afternote.domain.deepthought.service;

import com.afternote.domain.deepthought.dto.DeepThoughtCreateRequest;
import com.afternote.domain.deepthought.dto.DeepThoughtListResponse;
import com.afternote.domain.deepthought.dto.DeepThoughtResponse;
import com.afternote.domain.deepthought.dto.DeepThoughtUpdateRequest;
import com.afternote.domain.deepthought.model.DeepThought;
import com.afternote.domain.deepthought.model.DeepThoughtCategory;
import com.afternote.domain.deepthought.repository.DeepThoughtCategoryRepository;
import com.afternote.domain.deepthought.repository.DeepThoughtRepository;
import com.afternote.domain.mindrecord.emotion.event.DeepThoughtEmotionAnalysisRequestedEvent;
import com.afternote.domain.receiver.dto.MindRecordReceiverSummaryResponse;
import com.afternote.domain.receiver.repository.DeepThoughtReceiverRepository;
import com.afternote.domain.receiver.service.MindRecordReceiverService;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import com.afternote.global.sanitizer.MindRecordHtmlSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeepThoughtService {
    private final DeepThoughtCategoryRepository deepThoughtCategoryRepository;
    private final DeepThoughtRepository deepThoughtRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;
    private final MindRecordHtmlSanitizer mindRecordHtmlSanitizer;
    private final DeepThoughtReceiverRepository deepThoughtReceiverRepository;
    private final MindRecordReceiverService mindRecordReceiverService;

    @Transactional
    public DeepThoughtResponse createDeepThought(Long userId, DeepThoughtCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        DeepThoughtCategory category = getCategory(userId, request.getCategory());

        DeepThought deepThought = DeepThought.create(
                user,
                request.getTitle(),
                mindRecordHtmlSanitizer.sanitize(request.getContent()),
                request.getIsDraft(),
                request.getImageUrl(),
                category,
                request.getTags()
        );

        DeepThought saved = deepThoughtRepository.save(deepThought);
        if (Boolean.FALSE.equals(saved.getIsDraft())) {
            eventPublisher.publishEvent(new DeepThoughtEmotionAnalysisRequestedEvent(userId, saved.getId()));
        }

        List<MindRecordReceiverSummaryResponse> receivers = mindRecordReceiverService.replaceDeepThoughtReceivers(
                userId,
                saved,
                request.getReceiverIds(),
                Boolean.FALSE.equals(saved.getIsDraft())
        );
        return DeepThoughtResponse.from(saved, receivers);
    }

    @Transactional
    public DeepThoughtResponse updateDeepThought(Long userId, Long deepThoughtId, DeepThoughtUpdateRequest request) {
        DeepThought deepThought = deepThoughtRepository.findByIdAndUserId(deepThoughtId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.DEEP_THOUGHT_NOT_FOUND));

        DeepThoughtCategory category = null;
        if (request.getCategory() != null) {
            category = getCategory(userId, request.getCategory());
        }

        deepThought.update(
                request.getTitle(),
                request.getContent() != null ? mindRecordHtmlSanitizer.sanitize(request.getContent()) : null,
                request.getIsDraft(),
                request.getImageUrl(),
                category,
                request.getTags()
        );

        if (Boolean.FALSE.equals(deepThought.getIsDraft())) {
            eventPublisher.publishEvent(new DeepThoughtEmotionAnalysisRequestedEvent(userId, deepThought.getId()));
        }

        List<MindRecordReceiverSummaryResponse> receivers;
        if (request.getReceiverIds() != null) {
            receivers = mindRecordReceiverService.replaceDeepThoughtReceivers(
                    userId,
                    deepThought,
                    request.getReceiverIds(),
                    Boolean.FALSE.equals(deepThought.getIsDraft())
            );
        } else {
            receivers = mindRecordReceiverService.getDeepThoughtReceivers(deepThought.getId());
        }

        return DeepThoughtResponse.from(deepThought, receivers);
    }

    private DeepThoughtCategory getCategory(Long userId, String categoryTitle) {
        String normalizedCategory = categoryTitle == null ? null : categoryTitle.trim();
        if (normalizedCategory == null || normalizedCategory.isBlank()) {
            throw new CustomException(ErrorCode.DEEP_THOUGHT_CATEGORY_REQUIRED);
        }

        return deepThoughtCategoryRepository.findByUserIdAndTitle(userId, normalizedCategory)
                .orElseThrow(() -> new CustomException(ErrorCode.DEEP_THOUGHT_CATEGORY_NOT_FOUND));
    }

    public DeepThoughtListResponse getDeepThoughts(Long userId, LocalDate date, String tag, String category, Boolean draftOnly) {
        LocalDateTime start = null;
        LocalDateTime end = null;
        if (date != null) {
            start = date.atStartOfDay();
            end = date.plusDays(1).atStartOfDay();
        }

        String normalizedCategory = normalizeSearchParam(category);
        String normalizedTag = normalizeSearchParam(tag);

        List<DeepThought> thoughts = deepThoughtRepository.search(
                        userId, start, end, normalizedTag, normalizedCategory, draftOnly);

        List<Long> deepThoughtIds = thoughts.stream().map(DeepThought::getId).toList();
        Map<Long, List<MindRecordReceiverSummaryResponse>> receiversMap =
                mindRecordReceiverService.getDeepThoughtReceiversMap(deepThoughtIds);

        List<DeepThoughtResponse> list = thoughts.stream()
                .map(thought -> DeepThoughtResponse.from(
                        thought,
                        receiversMap.getOrDefault(thought.getId(), List.of())
                ))
                .toList();

        var tagCounts = deepThoughtRepository.aggregateTagCounts(
                userId, start, end, normalizedCategory, draftOnly);

        return DeepThoughtListResponse.from(list, tagCounts);
    }

    private String normalizeSearchParam(String value) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }
        return value.trim();
    }

    public DeepThoughtResponse getRandomDeepThought(Long userId) {
        long count = deepThoughtRepository.countByUserId(userId);
        if (count <= 0) {
            throw new CustomException(ErrorCode.DEEP_THOUGHT_NOT_FOUND);
        }

        int randomOffset = ThreadLocalRandom.current().nextInt((int) count);
        DeepThought deepThought = deepThoughtRepository.findByUserIdWithOffset(userId, randomOffset)
                .orElseThrow(() -> new CustomException(ErrorCode.DEEP_THOUGHT_NOT_FOUND));

        return DeepThoughtResponse.from(
                deepThought,
                mindRecordReceiverService.getDeepThoughtReceivers(deepThought.getId())
        );
    }

    @Transactional
    public void deleteDeepThought(Long userId, Long deepThoughtId) {
        DeepThought deepThought = deepThoughtRepository.findByIdAndUserId(deepThoughtId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.DEEP_THOUGHT_NOT_FOUND));
        deepThoughtReceiverRepository.deleteByDeepThoughtId(deepThoughtId);
        deepThoughtRepository.delete(deepThought);
    }

    public DeepThought getOwnedDeepThought(Long userId, Long deepThoughtId) {
        return deepThoughtRepository.findByIdAndUserId(deepThoughtId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.DEEP_THOUGHT_NOT_FOUND));
    }
}
