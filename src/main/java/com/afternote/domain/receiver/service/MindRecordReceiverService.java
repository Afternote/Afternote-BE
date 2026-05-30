package com.afternote.domain.receiver.service;

import com.afternote.domain.dailyquestion.model.UserDailyQuestion;
import com.afternote.domain.deepthought.model.DeepThought;
import com.afternote.domain.diary.model.Diary;
import com.afternote.domain.receiver.dto.MindRecordReceiverSummaryResponse;
import com.afternote.domain.receiver.model.DeepThoughtReceiver;
import com.afternote.domain.receiver.model.DiaryReceiver;
import com.afternote.domain.receiver.model.Receiver;
import com.afternote.domain.receiver.model.UserDailyQuestionReceiver;
import com.afternote.domain.receiver.repository.DeepThoughtReceiverRepository;
import com.afternote.domain.receiver.repository.DiaryReceiverRepository;
import com.afternote.domain.receiver.repository.ReceiverRepository;
import com.afternote.domain.receiver.repository.UserDailyQuestionReceiverRepository;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MindRecordReceiverService {

    private final ReceiverRepository receiverRepository;
    private final DiaryReceiverRepository diaryReceiverRepository;
    private final DeepThoughtReceiverRepository deepThoughtReceiverRepository;
    private final UserDailyQuestionReceiverRepository userDailyQuestionReceiverRepository;

    @Transactional
    public List<MindRecordReceiverSummaryResponse> replaceDiaryReceivers(
            Long userId,
            Diary diary,
            List<Long> receiverIds,
            boolean requireAtLeastOne
    ) {
        List<Long> normalizedIds = normalizeReceiverIds(receiverIds, requireAtLeastOne);
        diaryReceiverRepository.deleteByDiaryId(diary.getId());
        return saveDiaryReceivers(userId, diary, normalizedIds);
    }

    @Transactional
    public List<MindRecordReceiverSummaryResponse> replaceDeepThoughtReceivers(
            Long userId,
            DeepThought deepThought,
            List<Long> receiverIds,
            boolean requireAtLeastOne
    ) {
        List<Long> normalizedIds = normalizeReceiverIds(receiverIds, requireAtLeastOne);
        deepThoughtReceiverRepository.deleteByDeepThoughtId(deepThought.getId());
        return saveDeepThoughtReceivers(userId, deepThought, normalizedIds);
    }

    @Transactional
    public List<MindRecordReceiverSummaryResponse> replaceUserDailyQuestionReceivers(
            Long userId,
            UserDailyQuestion userDailyQuestion,
            List<Long> receiverIds,
            boolean requireAtLeastOne
    ) {
        List<Long> normalizedIds = normalizeReceiverIds(receiverIds, requireAtLeastOne);
        userDailyQuestionReceiverRepository.deleteByUserDailyQuestionId(userDailyQuestion.getId());
        return saveUserDailyQuestionReceivers(userId, userDailyQuestion, normalizedIds);
    }

    public List<MindRecordReceiverSummaryResponse> getDiaryReceivers(Long diaryId) {
        if (diaryId == null) {
            return List.of();
        }
        return diaryReceiverRepository.findByDiaryIdIn(List.of(diaryId)).stream()
                .map(link -> MindRecordReceiverSummaryResponse.from(link.getReceiver()))
                .toList();
    }

    public Map<Long, List<MindRecordReceiverSummaryResponse>> getDiaryReceiversMap(List<Long> diaryIds) {
        if (diaryIds == null || diaryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return groupDiaryReceivers(diaryReceiverRepository.findByDiaryIdIn(diaryIds));
    }

    public List<MindRecordReceiverSummaryResponse> getDeepThoughtReceivers(Long deepThoughtId) {
        if (deepThoughtId == null) {
            return List.of();
        }
        return deepThoughtReceiverRepository.findByDeepThoughtIdIn(List.of(deepThoughtId)).stream()
                .map(link -> MindRecordReceiverSummaryResponse.from(link.getReceiver()))
                .toList();
    }

    public Map<Long, List<MindRecordReceiverSummaryResponse>> getDeepThoughtReceiversMap(List<Long> deepThoughtIds) {
        if (deepThoughtIds == null || deepThoughtIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return groupDeepThoughtReceivers(deepThoughtReceiverRepository.findByDeepThoughtIdIn(deepThoughtIds));
    }

    public List<MindRecordReceiverSummaryResponse> getUserDailyQuestionReceivers(Long userDailyQuestionId) {
        if (userDailyQuestionId == null) {
            return List.of();
        }
        return userDailyQuestionReceiverRepository.findByUserDailyQuestionIdIn(List.of(userDailyQuestionId)).stream()
                .map(link -> MindRecordReceiverSummaryResponse.from(link.getReceiver()))
                .toList();
    }

    public Map<Long, List<MindRecordReceiverSummaryResponse>> getUserDailyQuestionReceiversMap(
            List<Long> userDailyQuestionIds
    ) {
        if (userDailyQuestionIds == null || userDailyQuestionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return groupUserDailyQuestionReceivers(
                userDailyQuestionReceiverRepository.findByUserDailyQuestionIdIn(userDailyQuestionIds)
        );
    }

    private List<MindRecordReceiverSummaryResponse> saveDiaryReceivers(
            Long userId,
            Diary diary,
            List<Long> normalizedIds
    ) {
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        List<Receiver> receivers = findOwnedReceivers(userId, normalizedIds);
        List<DiaryReceiver> links = receivers.stream()
                .map(receiver -> DiaryReceiver.builder()
                        .diary(diary)
                        .receiver(receiver)
                        .build())
                .toList();
        diaryReceiverRepository.saveAll(links);
        return toReceiverSummaries(receivers);
    }

    private List<MindRecordReceiverSummaryResponse> saveDeepThoughtReceivers(
            Long userId,
            DeepThought deepThought,
            List<Long> normalizedIds
    ) {
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        List<Receiver> receivers = findOwnedReceivers(userId, normalizedIds);
        List<DeepThoughtReceiver> links = receivers.stream()
                .map(receiver -> DeepThoughtReceiver.builder()
                        .deepThought(deepThought)
                        .receiver(receiver)
                        .build())
                .toList();
        deepThoughtReceiverRepository.saveAll(links);
        return toReceiverSummaries(receivers);
    }

    private List<MindRecordReceiverSummaryResponse> saveUserDailyQuestionReceivers(
            Long userId,
            UserDailyQuestion userDailyQuestion,
            List<Long> normalizedIds
    ) {
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        List<Receiver> receivers = findOwnedReceivers(userId, normalizedIds);
        List<UserDailyQuestionReceiver> links = receivers.stream()
                .map(receiver -> UserDailyQuestionReceiver.builder()
                        .userDailyQuestion(userDailyQuestion)
                        .receiver(receiver)
                        .build())
                .toList();
        userDailyQuestionReceiverRepository.saveAll(links);
        return toReceiverSummaries(receivers);
    }

    private List<Long> normalizeReceiverIds(List<Long> receiverIds, boolean requireAtLeastOne) {
        List<Long> uniqueReceiverIds = new ArrayList<>(new LinkedHashSet<>(
                receiverIds == null
                        ? List.of()
                        : receiverIds.stream().filter(Objects::nonNull).toList()
        ));

        if (requireAtLeastOne && uniqueReceiverIds.isEmpty()) {
            throw new CustomException(ErrorCode.RECEIVERS_REQUIRED);
        }

        return uniqueReceiverIds;
    }

    private List<Receiver> findOwnedReceivers(Long userId, List<Long> receiverIds) {
        List<Receiver> receivers = receiverRepository.findAllById(receiverIds);
        if (receivers.size() != receiverIds.size()) {
            throw new CustomException(ErrorCode.RECEIVER_NOT_FOUND);
        }

        boolean hasUnauthorizedReceiver = receivers.stream()
                .anyMatch(receiver -> !receiver.getUserId().equals(userId));
        if (hasUnauthorizedReceiver) {
            throw new CustomException(ErrorCode.NOT_ENOUGH_PERMISSION);
        }

        Map<Long, Receiver> receiverMap = receivers.stream()
                .collect(Collectors.toMap(Receiver::getId, receiver -> receiver));
        return receiverIds.stream()
                .map(receiverMap::get)
                .toList();
    }

    private List<MindRecordReceiverSummaryResponse> toReceiverSummaries(List<Receiver> receivers) {
        return receivers.stream()
                .map(MindRecordReceiverSummaryResponse::from)
                .toList();
    }

    private Map<Long, List<MindRecordReceiverSummaryResponse>> groupDiaryReceivers(List<DiaryReceiver> links) {
        if (links.isEmpty()) {
            return Collections.emptyMap();
        }
        return links.stream()
                .collect(Collectors.groupingBy(
                        link -> link.getDiary().getId(),
                        Collectors.mapping(
                                link -> MindRecordReceiverSummaryResponse.from(link.getReceiver()),
                                Collectors.toList()
                        )
                ));
    }

    private Map<Long, List<MindRecordReceiverSummaryResponse>> groupDeepThoughtReceivers(
            List<DeepThoughtReceiver> links
    ) {
        if (links.isEmpty()) {
            return Collections.emptyMap();
        }
        return links.stream()
                .collect(Collectors.groupingBy(
                        link -> link.getDeepThought().getId(),
                        Collectors.mapping(
                                link -> MindRecordReceiverSummaryResponse.from(link.getReceiver()),
                                Collectors.toList()
                        )
                ));
    }

    private Map<Long, List<MindRecordReceiverSummaryResponse>> groupUserDailyQuestionReceivers(
            List<UserDailyQuestionReceiver> links
    ) {
        if (links.isEmpty()) {
            return Collections.emptyMap();
        }
        return links.stream()
                .collect(Collectors.groupingBy(
                        link -> link.getUserDailyQuestion().getId(),
                        Collectors.mapping(
                                link -> MindRecordReceiverSummaryResponse.from(link.getReceiver()),
                                Collectors.toList()
                        )
                ));
    }
}
