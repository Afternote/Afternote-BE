package com.afternote.domain.receiver.service;

import com.afternote.domain.afternote.model.Afternote;
import com.afternote.domain.afternote.model.AfternoteReceiver;
import com.afternote.domain.dailyquestion.dto.DailyQuestionListResponse;
import com.afternote.domain.dailyquestion.model.UserDailyQuestion;
import com.afternote.domain.dailyquestion.repository.UserDailyQuestionRepository;
import com.afternote.domain.deepthought.dto.DeepThoughtResponse;
import com.afternote.domain.deepthought.dto.DeepThoughtTagCountResponse;
import com.afternote.domain.deepthought.model.DeepThought;
import com.afternote.domain.deepthought.repository.DeepThoughtRepository;
import com.afternote.domain.delivery.model.DeliveryContentType;
import com.afternote.domain.delivery.service.DeliveryConditionService;
import com.afternote.domain.diary.dto.DiaryResponse;
import com.afternote.domain.diary.model.Diary;
import com.afternote.domain.diary.repository.DiaryRepository;
import com.afternote.domain.image.service.S3Service;
import com.afternote.domain.receiver.dto.*;
import com.afternote.domain.receiver.model.*;
import com.afternote.domain.receiver.repository.*;
import com.afternote.domain.timeletter.model.TimeLetter;
import com.afternote.domain.timeletter.model.TimeLetterStatus;
import com.afternote.domain.timeletter.repository.TimeLetterRepository;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReceivedService {

    private final ReceiverRepository receiverRepository;
    private final TimeLetterReceiverRepository timeLetterReceiverRepository;
    private final AfternoteReceiverRepository afternoteReceiverRepository;
    private final DeepThoughtReceiverRepository deepThoughtReceiverRepository;
    private final DiaryReceiverRepository diaryReceiverRepository;
    private final UserDailyQuestionReceiverRepository userDailyQuestionReceiverRepository;
    private final TimeLetterRepository timeLetterRepository;
    private final DeepThoughtRepository deepThoughtRepository;
    private final DiaryRepository diaryRepository;
    private final UserDailyQuestionRepository userDailyQuestionRepository;
    private final UserRepository userRepository;
    private final S3Service s3Service;
    private final MindRecordReceiverService mindRecordReceiverService;
    private final DeliveryConditionService deliveryConditionService;

    private static final DateTimeFormatter KOREAN_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy.MM.dd E", Locale.KOREAN);

    /**
     * 수신자가 받은 타임레터 목록 조회
     * - DATE는 sendAt 기준으로 본문 공개. POST_DEATH는 TIME_LETTER 조건 충족 시 애프터노트와 같이 공개.
     * - DRAFT는 수신 목록에 넣지 않는다.
     */
    public ReceivedTimeLetterListResponse getTimeLetters(Long receiverId) {
        // 타임레터는 DATE 모드는 날짜 기반(조건 무관), POST_DEATH 모드만 사후 조건으로 게이팅한다.
        boolean postDeathFulfilled =
                deliveryConditionService.isFulfilled(receiverId, DeliveryContentType.TIME_LETTER);

        List<TimeLetterReceiver> timeLetterReceivers =
                timeLetterReceiverRepository.findByReceiverIdWithTimeLetter(receiverId);

        List<ReceivedTimeLetterResponse> responses = timeLetterReceivers.stream()
                .filter(tlr -> tlr.getTimeLetter().getStatus() != TimeLetterStatus.DRAFT)
                .filter(tlr -> !tlr.getTimeLetter().isPostDeath() || postDeathFulfilled)
                .map(tlr -> ReceivedTimeLetterResponse.from(
                        tlr,
                        s3Service::generateGetPresignedUrl
                ))
                .toList();

        return ReceivedTimeLetterListResponse.from(responses);
    }

    /**
     * 수신한 타임레터 상세 조회
     * - 수신자 본인에게 연결된 타임레터인지 검증한다.
     * - DATE는 sendAt이 지난 경우에만 읽음 처리한다.
     * - POST_DEATH는 TIME_LETTER 조건 충족 시 읽음 처리한다.
     * - DRAFT는 열람할 수 없다.
     */
    @Transactional
    public ReceivedTimeLetterResponse getTimeLetter(Long receiverId, Long timeLetterReceiverId) {
        TimeLetterReceiver timeLetterReceiver = timeLetterReceiverRepository
                .findByIdAndReceiverIdWithTimeLetter(timeLetterReceiverId, receiverId)
                .orElseThrow(() -> new CustomException(ErrorCode.TIME_LETTER_NOT_FOUND));

        TimeLetter timeLetter = timeLetterReceiver.getTimeLetter();
        if (timeLetter.getStatus() == TimeLetterStatus.DRAFT) {
            throw new CustomException(ErrorCode.TIME_LETTER_NOT_FOUND);
        }

        if (timeLetter.isPostDeath()) {
            // 사후 전달 타임레터는 전달 조건이 충족되어야 열람 가능
            deliveryConditionService.requireFulfilled(receiverId, DeliveryContentType.TIME_LETTER);
            timeLetterReceiver.markAsRead();
        } else if (timeLetter.getSendAt() != null
                && !timeLetter.getSendAt().isAfter(LocalDateTime.now())) {
            // 발송 예정 시간이 지났을 때만 읽음 처리
            timeLetterReceiver.markAsRead();
        }

        return ReceivedTimeLetterResponse.from(
                timeLetterReceiver,
                s3Service::generateGetPresignedUrl
        );
    }

    /**
     * 수신자가 받은 애프터노트 목록 조회
     */
    public ReceivedAfternoteListResponse getAfternotes(Long receiverId) {
        deliveryConditionService.requireFulfilled(receiverId, DeliveryContentType.AFTERNOTE);

        List<AfternoteReceiver> afternoteReceivers =
                afternoteReceiverRepository.findByReceiverIdWithAfternote(receiverId);

        // Afternote은 User 객체를 가지고 있으므로 User 정보를 직접 조회
        Set<Long> userIds = afternoteReceivers.stream()
                .map(ar -> ar.getAfternote().getUser().getId())
                .collect(Collectors.toSet());

        var userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<ReceivedAfternoteResponse> responses = afternoteReceivers.stream()
                .map(ar -> {
                    User sender = userMap.get(ar.getAfternote().getUser().getId());
                    String senderName = sender != null ? sender.getName() : "알 수 없음";
                    return ReceivedAfternoteResponse.from(ar, senderName);
                })
                .toList();

        return ReceivedAfternoteListResponse.from(responses);
    }

    /**
     * 수신한 애프터노트 상세 조회
     */
    public ReceivedAfternoteDetailResponse getAfternote(Long receiverId, Long afternoteId) {
        deliveryConditionService.requireFulfilled(receiverId, DeliveryContentType.AFTERNOTE);

        AfternoteReceiver afternoteReceiver = afternoteReceiverRepository
                .findByAfternoteIdAndReceiverIdWithAfternote(afternoteId, receiverId)
                .orElseThrow(() -> new CustomException(ErrorCode.AFTERNOTE_NOT_FOUND));

        Afternote afternote = afternoteReceiver.getAfternote();

        User sender = userRepository.findById(afternote.getUser().getId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String senderName = sender.getName();

        return switch (afternote.getCategoryType()) {
            case SOCIAL, BUSINESS -> ReceivedAfternoteDetailResponse.fromSocial(afternote, senderName);
            case GALLERY -> ReceivedAfternoteDetailResponse.fromGallery(afternote, senderName);
            case PLAYLIST -> ReceivedAfternoteDetailResponse.fromPlaylist(
                    afternote,
                    senderName,
                    s3Service::resolvePublicUrl
            );
        };
    }

    /**
     * 타임레터에 수신자 등록
     * - 별도 수신자 등록 API에서 사용한다.
     * - timeLetterId 기준으로 타임레터 소유권을 검증한다.
     * - receiverIds의 존재 여부와 소유권을 검증한 뒤 TimeLetterReceiver를 생성한다.
     */
    @Transactional
    public List<Long> createTimeLetterReceivers(Long userId, CreateTimeLetterReceiverRequest request) {
        TimeLetter timeLetter = timeLetterRepository.findByIdAndUserId(request.getTimeLetterID(), userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TIME_LETTER_NOT_FOUND));

        List<Long> uniqueReceiverIds = normalizeReceiverIds(request.getReceiverIds());

        List<Receiver> receivers = receiverRepository.findAllById(uniqueReceiverIds);
        if (receivers.size() != uniqueReceiverIds.size()) {
            throw new CustomException(ErrorCode.RECEIVER_NOT_FOUND);
        }

        // 본인이 등록한 수신자인지 검증
        validateReceiversOwnership(userId, receivers);

        // deliveredAt이 없으면 타임레터의 sendAt으로 대체
        LocalDateTime effectiveDeliveredAt = request.getDeliveredAt() != null
                ? request.getDeliveredAt()
                : timeLetter.getSendAt();

        if (effectiveDeliveredAt == null) {
            throw new CustomException(ErrorCode.TIME_LETTER_REQUIRED_FIELDS);
        }

        List<TimeLetterReceiver> timeLetterReceivers = receivers.stream()
                .map(receiver -> TimeLetterReceiver.builder()
                        .timeLetter(timeLetter)
                        .receiver(receiver)
                        .deliveredAt(effectiveDeliveredAt)
                        .build())
                .toList();

        return timeLetterReceiverRepository.saveAll(timeLetterReceivers).stream()
                .map(TimeLetterReceiver::getId)
                .toList();
    }

    /**
     * 타임레터 생성 시 수신자 등록
     * - TimeLetterService에서 타임레터 생성과 함께 호출한다.
     * - 전제조건: 호출자가 TimeLetter의 소유권을 이미 검증했거나, 방금 생성한 TimeLetter를 전달해야 한다.
     */
    @Transactional
    public List<Long> createTimeLetterReceivers(
            TimeLetter timeLetter,
            Long userId,
            List<Long> receiverIds,
            LocalDateTime deliveredAt
    ) {
        List<Long> uniqueReceiverIds = normalizeReceiverIds(receiverIds);

        List<Receiver> receivers = receiverRepository.findAllById(uniqueReceiverIds);
        if (receivers.size() != uniqueReceiverIds.size()) {
            throw new CustomException(ErrorCode.RECEIVER_NOT_FOUND);
        }

        // 본인이 등록한 수신자인지 검증
        validateReceiversOwnership(userId, receivers);

        // deliveredAt이 null이면 timeLetter의 sendAt으로 대체
        LocalDateTime effectiveDeliveredAt = deliveredAt != null
                ? deliveredAt
                : timeLetter.getSendAt();

        // POST_DEATH 모드는 발송일이 없으므로 deliveredAt이 null이어도 허용한다.
        if (effectiveDeliveredAt == null && !timeLetter.isPostDeath()) {
            throw new CustomException(ErrorCode.TIME_LETTER_REQUIRED_FIELDS);
        }

        List<TimeLetterReceiver> timeLetterReceivers = receivers.stream()
                .map(receiver -> TimeLetterReceiver.builder()
                        .timeLetter(timeLetter)
                        .receiver(receiver)
                        .deliveredAt(effectiveDeliveredAt)
                        .build())
                .toList();

        return timeLetterReceiverRepository.saveAll(timeLetterReceivers).stream()
                .map(TimeLetterReceiver::getId)
                .toList();
    }

    /**
     * 깊은 생각에 수신자 등록
     */
    @Transactional
    public List<Long> createDeepThoughtReceivers(Long userId, CreateDeepThoughtReceiverRequest request) {
        DeepThought deepThought = deepThoughtRepository.findByIdAndUserId(request.getDeepThoughtId(), userId)
                .orElseThrow(() -> new CustomException(ErrorCode.DEEP_THOUGHT_NOT_FOUND));

        mindRecordReceiverService.replaceDeepThoughtReceivers(
                userId,
                deepThought,
                request.getReceiverIds(),
                true
        );

        return deepThoughtReceiverRepository.findByDeepThoughtIdIn(List.of(deepThought.getId())).stream()
                .map(DeepThoughtReceiver::getId)
                .toList();
    }

    /**
     * 다이어리에 수신자 등록
     */
    @Transactional
    public List<Long> createDiaryReceivers(Long userId, CreateDiaryReceiverRequest request) {
        // 동일 일기 동시 연결: DELETE-all + INSERT 레이스를 행 락으로 직렬화 (#171)
        Diary diary = diaryRepository.findByIdAndUserIdForUpdate(request.getDiaryId(), userId)
                .orElseThrow(() -> new CustomException(ErrorCode.DIARY_NOT_FOUND));

        mindRecordReceiverService.replaceDiaryReceivers(
                userId,
                diary,
                request.getReceiverIds(),
                true
        );

        return diaryReceiverRepository.findByDiaryIdIn(List.of(diary.getId())).stream()
                .map(DiaryReceiver::getId)
                .toList();
    }

    /**
     * 데일리 질문 답변에 수신자 등록
     */
    @Transactional
    public List<Long> createUserDailyQuestionReceivers(Long userId, CreateUserDailyQuestionReceiverRequest request) {
        UserDailyQuestion userDailyQuestion = userDailyQuestionRepository.findById(request.getUserDailyQuestionId())
                .orElseThrow(() -> new CustomException(ErrorCode.DAILY_QUESTION_NOT_FOUND));

        if (!userDailyQuestion.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.NOT_ENOUGH_PERMISSION);
        }

        mindRecordReceiverService.replaceUserDailyQuestionReceivers(
                userId,
                userDailyQuestion,
                request.getReceiverIds(),
                true
        );

        return userDailyQuestionReceiverRepository.findByUserDailyQuestionIdIn(List.of(userDailyQuestion.getId())).stream()
                .map(UserDailyQuestionReceiver::getId)
                .toList();
    }

    /**
     * 수신자 ID 목록 정규화
     * - null 또는 빈 목록이면 예외를 발생시킨다.
     * - null 원소를 제거한다.
     * - 중복된 수신자 ID를 제거한다.
     */
    private List<Long> normalizeReceiverIds(List<Long> receiverIds) {
        if (receiverIds == null || receiverIds.isEmpty()) {
            throw new CustomException(ErrorCode.RECEIVERS_REQUIRED);
        }

        List<Long> uniqueReceiverIds = new ArrayList<>(new LinkedHashSet<>(
                receiverIds.stream()
                        .filter(Objects::nonNull)
                        .toList()
        ));

        if (uniqueReceiverIds.isEmpty()) {
            throw new CustomException(ErrorCode.RECEIVERS_REQUIRED);
        }

        return uniqueReceiverIds;
    }

    /**
     * 수신자가 본인이 등록한 수신자인지 검증
     */
    private void validateReceiversOwnership(Long userId, List<Receiver> receivers) {
        boolean hasUnauthorizedReceiver = receivers.stream()
                .anyMatch(receiver -> !receiver.getUserId().equals(userId));

        if (hasUnauthorizedReceiver) {
            throw new CustomException(ErrorCode.NOT_ENOUGH_PERMISSION);
        }
    }

    /**
     * 수신자가 받은 일기 목록 조회
     * - 수신자 ID 기준으로 DiaryReceiver 목록을 조회한다.
     * - 임시저장이 아닌 정식 등록 일기만 반환한다.
     * - startDate/endDate가 있으면 원본 일기 작성일 기준으로 기간 필터링한다.
     * - sort 값에 따라 최신순 또는 오래된순으로 정렬한다.
     */
    public ReceivedDiaryListResponse getReceivedDiaries(
            Long receiverId,
            ReceivedRecordSort sort,
            LocalDate startDate,
            LocalDate endDate
    ) {
        // 사후 전달 조건 검증 (다이어리)
        deliveryConditionService.requireFulfilled(receiverId, DeliveryContentType.DIARY);

        // 날짜 조건을 LocalDateTime 범위로 변환
        LocalDateTime start = toStartDateTime(startDate);
        LocalDateTime end = toEndDateTime(endDate);

        List<DiaryResponse> diaries = diaryReceiverRepository
                .findReceivedDiaries(receiverId, start, end)
                .stream()
                // 연결 엔티티에서 실제 Diary 엔티티만 꺼낸다.
                .map(DiaryReceiver::getDiary)
                // 요청한 정렬 기준에 따라 createdAt 기준 정렬
                .sorted(diaryComparator(sort))
                // 기존 Diary 응답 DTO로 변환
                .map(DiaryResponse::from)
                .toList();

        return ReceivedDiaryListResponse.from(diaries);
    }

    /**
     * 수신자가 받은 깊은 생각 목록 조회
     * - 수신자 ID 기준으로 DeepThoughtReceiver 목록을 조회한다.
     * - 임시저장이 아닌 정식 등록 깊은 생각만 반환한다.
     * - 기간, 카테고리, 태그 조건으로 필터링할 수 있다.
     * - 태그 검색어는 태그명뿐 아니라 제목/내용 검색에도 사용된다.
     * - 응답에는 깊은 생각 목록과 함께 카테고리 목록, 태그별 글 개수를 포함한다.
     */
    public ReceivedDeepThoughtListResponse getReceivedDeepThoughts(
            Long receiverId,
            String category,
            String tag,
            ReceivedRecordSort sort,
            LocalDate startDate,
            LocalDate endDate
    ) {
        // 사후 전달 조건 검증 (딥쏘트)
        deliveryConditionService.requireFulfilled(receiverId, DeliveryContentType.DEEP_THOUGHT);

        // 날짜 조건을 LocalDateTime 범위로 변환
        LocalDateTime start = toStartDateTime(startDate);
        LocalDateTime end = toEndDateTime(endDate);

        // 검색 조건 공백 제거 및 null 처리
        String normalizedCategory = normalizeSearchParam(category);
        String normalizedTag = normalizeTag(tag);

        List<DeepThought> thoughts = deepThoughtReceiverRepository
                .findReceivedDeepThoughts(receiverId, start, end, normalizedCategory, normalizedTag)
                .stream()
                // 연결 엔티티에서 실제 DeepThought 엔티티만 꺼낸다.
                .map(DeepThoughtReceiver::getDeepThought)
                // 태그 JOIN으로 인해 중복될 수 있는 깊은 생각을 제거
                .distinct()
                // 요청한 정렬 기준에 따라 createdAt 기준 정렬
                .sorted(deepThoughtComparator(sort))
                .toList();

        // 필터 UI에 사용할 카테고리 목록 조회
        List<String> categories = deepThoughtReceiverRepository
                .findReceivedCategoryTitles(receiverId, start, end);

        // 태그별 글 개수 조회
        // tag 검색어는 목록 필터에만 적용하고, 태그 개수 집계에는 적용하지 않는다.
        List<DeepThoughtTagCountResponse> tagCounts = deepThoughtReceiverRepository
                .aggregateReceivedTagCounts(receiverId, start, end, normalizedCategory);

        // 기존 DeepThought 응답 DTO로 변환
        List<DeepThoughtResponse> deepThoughts = thoughts.stream()
                .map(DeepThoughtResponse::from)
                .toList();

        return ReceivedDeepThoughtListResponse.from(categories, tagCounts, deepThoughts);
    }

    /**
     * 수신자가 받은 데일리 질문 답변 목록 조회
     * - 수신자 ID 기준으로 UserDailyQuestionReceiver 목록을 조회한다.
     * - 임시저장이 아니고 답변 완료된 데일리 질문만 반환한다.
     * - startDate/endDate가 있으면 답변 작성일 기준으로 기간 필터링한다.
     * - sort 값에 따라 최신순 또는 오래된순으로 정렬한다.
     */
    public ReceivedDailyQuestionListResponse getReceivedDailyQuestions(
            Long receiverId,
            ReceivedRecordSort sort,
            LocalDate startDate,
            LocalDate endDate
    ) {
        // 사후 전달 조건 검증 (데일리 질문)
        deliveryConditionService.requireFulfilled(receiverId, DeliveryContentType.DAILY_QUESTION);

        // 날짜 조건을 LocalDateTime 범위로 변환
        LocalDateTime start = toStartDateTime(startDate);
        LocalDateTime end = toEndDateTime(endDate);

        List<DailyQuestionListResponse> dailyQuestions = userDailyQuestionReceiverRepository
                .findReceivedDailyQuestions(receiverId, start, end)
                .stream()
                // 연결 엔티티에서 실제 UserDailyQuestion 엔티티만 꺼낸다.
                .map(UserDailyQuestionReceiver::getUserDailyQuestion)
                // 요청한 정렬 기준에 따라 createdAt 기준 정렬
                .sorted(userDailyQuestionComparator(sort))
                // 데일리 질문 목록 응답 DTO로 변환
                .map(this::toDailyQuestionListResponse)
                .toList();

        return ReceivedDailyQuestionListResponse.from(dailyQuestions);
    }

    /**
     * 조회 시작일을 LocalDateTime으로 변환
     * - startDate가 있으면 해당 날짜의 00:00:00으로 변환한다.
     * - 없으면 Repository 조건에서 필터링하지 않도록 null을 반환한다.
     */
    private LocalDateTime toStartDateTime(LocalDate startDate) {
        return startDate != null ? startDate.atStartOfDay() : null;
    }

    /**
     * 조회 종료일을 LocalDateTime으로 변환
     * - endDate가 있으면 다음 날 00:00:00으로 변환한다.
     * - Repository에서는 '< end' 조건을 사용하므로 종료일 당일까지 포함된다.
     * - 없으면 Repository 조건에서 필터링하지 않도록 null을 반환한다.
     */
    private LocalDateTime toEndDateTime(LocalDate endDate) {
        return endDate != null ? endDate.plusDays(1).atStartOfDay() : null;
    }

    /**
     * 검색 파라미터 정규화
     * - null 또는 공백 문자열이면 검색 조건을 적용하지 않도록 null을 반환한다.
     * - 값이 있으면 앞뒤 공백을 제거한다.
     */
    private String normalizeSearchParam(String value) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * 태그 검색어 정규화
     * - null 또는 공백 문자열이면 태그 검색 조건을 적용하지 않도록 null을 반환한다.
     * - 프론트에서 '#성장'처럼 전달해도 DB 값과 비교할 수 있도록 '#'을 제거한다.
     */
    private String normalizeTag(String tag) {
        if (tag == null || tag.trim().isBlank()) {
            return null;
        }

        String normalized = tag.trim();
        return normalized.startsWith("#") ? normalized.substring(1) : normalized;
    }

    /**
     * 일기 정렬 조건 생성
     * - LATEST이면 createdAt 기준 최신순
     * - OLDEST이면 createdAt 기준 오래된순
     */
    private Comparator<Diary> diaryComparator(ReceivedRecordSort sort) {
        Comparator<Diary> comparator = Comparator.comparing(Diary::getCreatedAt);
        return sort == ReceivedRecordSort.OLDEST ? comparator : comparator.reversed();
    }

    /**
     * 깊은 생각 정렬 조건 생성
     * - LATEST이면 createdAt 기준 최신순
     * - OLDEST이면 createdAt 기준 오래된순
     */
    private Comparator<DeepThought> deepThoughtComparator(ReceivedRecordSort sort) {
        Comparator<DeepThought> comparator = Comparator.comparing(DeepThought::getCreatedAt);
        return sort == ReceivedRecordSort.OLDEST ? comparator : comparator.reversed();
    }

    /**
     * 데일리 질문 답변 정렬 조건 생성
     * - LATEST이면 createdAt 기준 최신순
     * - OLDEST이면 createdAt 기준 오래된순
     */
    private Comparator<UserDailyQuestion> userDailyQuestionComparator(ReceivedRecordSort sort) {
        Comparator<UserDailyQuestion> comparator = Comparator.comparing(UserDailyQuestion::getCreatedAt);
        return sort == ReceivedRecordSort.OLDEST ? comparator : comparator.reversed();
    }

    /**
     * UserDailyQuestion 엔티티를 목록 응답 DTO로 변환
     * - title에는 원본 데일리 질문 내용을 담는다.
     * - content에는 사용자가 작성한 답변 내용을 담는다.
     * - createdAt은 기존 데일리 질문 목록과 동일한 yyyy.MM.dd E 형식으로 변환한다.
     */
    private DailyQuestionListResponse toDailyQuestionListResponse(UserDailyQuestion userDailyQuestion) {
        return DailyQuestionListResponse.builder()
                .userDailyQuestionId(userDailyQuestion.getId())
                .title(userDailyQuestion.getDailyQuestion().getContent())
                .content(userDailyQuestion.getContent())
                .createdAt(userDailyQuestion.getCreatedAt() != null
                        ? userDailyQuestion.getCreatedAt().format(KOREAN_DATE_FORMATTER)
                        : null)
                .build();
    }
}
