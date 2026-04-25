package com.afternote.domain.deepthought.service;

import com.afternote.domain.deepthought.dto.DeepThoughtCreateRequest;
import com.afternote.domain.deepthought.dto.DeepThoughtListResponse;
import com.afternote.domain.deepthought.dto.DeepThoughtResponse;
import com.afternote.domain.deepthought.model.DeepThought;
import com.afternote.domain.deepthought.repository.DeepThoughtRepository;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeepThoughtService {
    private final DeepThoughtRepository deepThoughtRepository;
    private final UserRepository userRepository;

    @Transactional
    public DeepThoughtResponse createDeepThought(Long userId, DeepThoughtCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        DeepThought deepThought = DeepThought.create(
                user,
                request.getTitle(),
                request.getContent(),
                request.getIsDraft(),
                request.getImageUrl(),
                request.getCategory(),
                request.getTags()
        );

        DeepThought saved = deepThoughtRepository.save(deepThought);

        return DeepThoughtResponse.from(saved);
    }

    public DeepThoughtListResponse getDeepThoughts(Long userId, LocalDate date, String tag, String category) {
        LocalDateTime start = null;
        LocalDateTime end = null;
        if (date != null) {
            start = date.atStartOfDay();
            end = date.plusDays(1).atStartOfDay();
        }

        List<DeepThoughtResponse> list = deepThoughtRepository.search(userId, start, end, tag, category)
                .stream()
                .map(DeepThoughtResponse::from)
                .toList();

        return DeepThoughtListResponse.from(list);
    }

    public DeepThoughtResponse getRandomDeepThought(Long userId) {
        long count = deepThoughtRepository.countByUserId(userId);
        if (count <= 0) {
            throw new CustomException(ErrorCode.MIND_RECORD_NOT_FOUND);
        }

        int randomOffset = ThreadLocalRandom.current().nextInt((int) count);
        DeepThought deepThought = deepThoughtRepository.findByUserIdWithOffset(userId, randomOffset)
                .orElseThrow(() -> new CustomException(ErrorCode.MIND_RECORD_NOT_FOUND));

        return DeepThoughtResponse.from(deepThought);
    }

    @Transactional
    public void deleteDeepThought(Long userId, Long deepThoughtId) {
        DeepThought deepThought = deepThoughtRepository.findByIdAndUserId(deepThoughtId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.MIND_RECORD_NOT_FOUND));
        deepThoughtRepository.delete(deepThought);
    }

    public DeepThought getOwnedDeepThought(Long userId, Long deepThoughtId) {
        return deepThoughtRepository.findByIdAndUserId(deepThoughtId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.MIND_RECORD_NOT_FOUND));
    }
}
