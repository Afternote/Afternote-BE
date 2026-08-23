package com.afternote.domain.afternote.service;

import com.afternote.domain.afternote.dto.*;
import com.afternote.domain.afternote.model.*;
import com.afternote.domain.afternote.repository.AfternoteRepository;
import com.afternote.domain.afternote.service.relation.EncryptedKey;
import com.afternote.domain.image.service.S3Service;
import com.afternote.domain.user.event.UserActivityTouchedEvent;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import com.afternote.global.util.ChaChaEncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AfternoteService {

    private final AfternoteRepository afternoteRepository;
    private final com.afternote.domain.user.repository.UserRepository userRepository;
    private final AfternoteRelationService relationService;
    private final AfternoteValidator validator;
    private final ChaChaEncryptionUtil chaChaEncryptionUtil;
    private final S3Service s3Service;
    private final ApplicationEventPublisher eventPublisher;

    public AfternotePageResponse getAfternotes(
            Long userId,
            AfternoteCategoryType category,
            Integer page,
            Integer size,
            Boolean draftOnly
    ) {
        if (page == null || page < 0 || size == null || size < 1) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        // PageRequest offset(page * size)가 int 범위를 넘으면 IllegalArgumentException → 500 방지
        if ((long) page * (long) size > Integer.MAX_VALUE) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        boolean isDraft = Boolean.TRUE.equals(draftOnly);
        Pageable pageable = PageRequest.of(page, size);
        Page<Afternote> afternotePage;
        
        if (category != null) {
            afternotePage = afternoteRepository.findByUserIdAndCategoryTypeAndIsDraftOrderByCreatedAtDesc(
                    userId, category, isDraft, pageable);
        } else {
            afternotePage = afternoteRepository.findByUserIdAndIsDraftOrderByCreatedAtDesc(
                    userId, isDraft, pageable);
        }
        
        List<AfternoteResponse> content = afternotePage.getContent().stream()
                .map(afternote -> AfternoteResponse.builder()
                        .afternoteId(afternote.getId())
                        .title(afternote.getTitle())
                        .category(afternote.getCategoryType())
                        .isDraft(Boolean.TRUE.equals(afternote.getIsDraft()))
                        .createdAt(afternote.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
        
        return AfternotePageResponse.builder()
                .content(content)
                .page(page)
                .size(size)
                .hasNext(afternotePage.hasNext())
                .build();
    }

    public AfternotedetailResponse getDetailAfternote(Long userId, Long afternoteId) {
        Afternote afternote = afternoteRepository.findById(afternoteId)
                .orElseThrow(() -> new CustomException(ErrorCode.AFTERNOTE_NOT_FOUND));
        if(!afternote.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.AFTERNOTE_NOT_FOUND);
        }
        
        // 모든 카테고리에서 공통으로 필요한 receivers 매핑
        List<AfternoteReceiverResponse> receivers = afternote.getReceivers().stream()
                .map(ar -> AfternoteReceiverResponse.from(ar.getReceiver()))
                .collect(Collectors.toList());
        
        AfternotedetailResponse response;
        
        // 카테고리별 데이터 조회 및 응답 생성
        switch (afternote.getCategoryType()) {
            case SOCIAL, BUSINESS:
                // secureContents에서 credentials 가져오고 복호화
                AfternoteCreateRequest.CredentialsRequest credentials = null;
                
                String accountId = afternote.getSecureContents().stream()
                        .filter(sc -> EncryptedKey.ACCOUNT_ID.matches(sc.getKeyName()))
                        .findFirst()
                        .map(sc -> chaChaEncryptionUtil.decrypt(sc.getEncryptedValue()))
                        .orElse(null);
                
                String accountPassword = afternote.getSecureContents().stream()
                        .filter(sc -> EncryptedKey.ACCOUNT_PASSWORD.matches(sc.getKeyName()))
                        .findFirst()
                        .map(sc -> chaChaEncryptionUtil.decrypt(sc.getEncryptedValue()))
                        .orElse(null);
                
                if (accountId != null || accountPassword != null) {
                    credentials = new AfternoteCreateRequest.CredentialsRequest(accountId, accountPassword);
                }
                
                response = new AfternotedetailResponse(
                        afternote.getId(),
                        afternote.getCategoryType(),
                        afternote.getTitle(),
                        Boolean.TRUE.equals(afternote.getIsDraft()),
                        afternote.getActions(),
                        afternote.getLeaveMessage(),
                        credentials,
                        receivers,
                        null,
                        afternote.getUpdatedAt()
                );
                break;
                
            case GALLERY:
                response = new AfternotedetailResponse(
                        afternote.getId(),
                        afternote.getCategoryType(),
                        afternote.getTitle(),
                        Boolean.TRUE.equals(afternote.getIsDraft()),
                        afternote.getActions(),
                        afternote.getLeaveMessage(),
                        null,
                        receivers,
                        null,
                        afternote.getUpdatedAt()
                );
                break;
                
            case PLAYLIST:
                // playlist 매핑
                AfternoteCreateRequest.PlaylistRequest playlistRequest = null;
                
                if (afternote.getPlaylist() != null) {
                    AfternotePlaylist playlist = afternote.getPlaylist();
                    
                    // songs 매핑
                    List<AfternoteCreateRequest.SongRequest> songs = playlist.getItems().stream()
                            .map(item -> new AfternoteCreateRequest.SongRequest(
                                    item.getSongTitle(),
                                    item.getArtist(),
                                    s3Service.generateGetPresignedUrl(item.getCoverUrl())
                            ))
                            .collect(Collectors.toList());

                    // memorialVideo 매핑 (videoUrl, thumbnailUrl presigned GET 변환)
                    AfternoteCreateRequest.MemorialVideoRequest memorialVideo = null;
                    if (playlist.getMemorialVideo() != null) {
                        memorialVideo = new AfternoteCreateRequest.MemorialVideoRequest(
                                s3Service.generateGetPresignedUrl(playlist.getMemorialVideo().getVideoUrl()),
                                s3Service.generateGetPresignedUrl(playlist.getMemorialVideo().getThumbnailUrl())
                        );
                    }

                    // memorialPhotoUrl presigned GET 변환
                    String memorialPhotoUrlPresigned = null;
                    if (playlist.getMemorialPhotoUrl() != null) {
                        memorialPhotoUrlPresigned = s3Service.generateGetPresignedUrl(playlist.getMemorialPhotoUrl());
                    }

                    playlistRequest = new AfternoteCreateRequest.PlaylistRequest(
                            playlist.getAtmosphere(),
                            memorialPhotoUrlPresigned,
                            songs,
                            memorialVideo
                    );
                }
                
                response = new AfternotedetailResponse(
                        afternote.getId(),
                        afternote.getCategoryType(),
                        afternote.getTitle(),
                        Boolean.TRUE.equals(afternote.getIsDraft()),
                        null,
                        afternote.getLeaveMessage(),
                        null,
                        receivers,
                        playlistRequest,
                        afternote.getUpdatedAt()
                );
                break;
                
            default:
                response = new AfternotedetailResponse(
                        afternote.getId(),
                        afternote.getCategoryType(),
                        afternote.getTitle(),
                        Boolean.TRUE.equals(afternote.getIsDraft()),
                        afternote.getActions(),
                        afternote.getLeaveMessage(),
                        null,
                        receivers,
                        null,
                        afternote.getUpdatedAt()
                );
        }
        
        return response;
    }

    @Transactional
    public AfternoteCreateResponse createAfternote(Long userId, AfternoteCreateRequest request) {
        // 요청 데이터 검증
        validator.validateCreateRequest(request);
        
        // 사용자 조회
        com.afternote.domain.user.model.User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        eventPublisher.publishEvent(new UserActivityTouchedEvent(userId));
        
        // sortOrder 자동 계산 (해당 사용자의 최대값 + 1)
        Integer nextSortOrder = afternoteRepository.findMaxSortOrderByUserId(userId)
                .map(max -> max + 1)
                .orElse(1);
        
        // 공통 필드로 Afternote 생성
        Afternote.AfternoteBuilder builder = Afternote.builder()
                .user(user)
                .categoryType(request.getCategory())
                .title(request.getTitle())
                .isDraft(request.isDraftValue())
                .sortOrder(nextSortOrder)
                .leaveMessage(request.getLeaveMessage());

        // SOCIAL/BUSINESS/GALLERY 전용 필드
        if (request.getCategory() == AfternoteCategoryType.SOCIAL
                || request.getCategory() == AfternoteCategoryType.BUSINESS
                || request.getCategory() == AfternoteCategoryType.GALLERY) {
            builder.actions(request.getActions() != null ? new ArrayList<>(request.getActions()) : new ArrayList<>());
        }

        Afternote afternote = builder.build();

        // ✅ 먼저 Afternote 저장 (ID 생성)
        Afternote saved = afternoteRepository.save(afternote);
        
        // ✅ 그 다음 카테고리별 관계 데이터 저장 (저장된 afternote 참조)
        relationService.saveRelationsByCategory(saved, request);
        
        return AfternoteCreateResponse.builder()
                .afternoteId(saved.getId())
                .build();
    }

    @Transactional
    public AfternoteCreateResponse updateAfternote(Long userId, Long afternoteId, AfternoteUpdateRequest request) {
        Afternote afternote = afternoteRepository.findById(afternoteId)
                .orElseThrow(() -> new CustomException(ErrorCode.AFTERNOTE_NOT_FOUND));
        if(!afternote.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.AFTERNOTE_ACCESS_DENIED);
        }

        AfternoteCategoryType storedCategory = afternote.getCategoryType();
        // category 변경 여부·필드 조합은 UpdateRequest 기준으로 검사
        validator.validateUpdateRequest(request, storedCategory);

        // 이후 계층은 CreateRequest 형태를 쓰되, category는 항상 저장값을 SSOT로 사용
        AfternoteCreateRequest writeRequest = request.toWriteRequest(storedCategory);
        // 정식 등록으로 남거나 전환될 때 필수값 검증 (요청 + 기존 엔티티)
        validator.validatePublishRequirements(writeRequest, afternote);

        // 기본 필드 업데이트 (null이 아닌 경우만)
        String title = writeRequest.getTitle() != null ? writeRequest.getTitle() : afternote.getTitle();
        List<LeaveMessageBlock> leaveMessage = writeRequest.getLeaveMessage() != null
                ? writeRequest.getLeaveMessage()
                : afternote.getLeaveMessage();

        // update()가 actions를 clear 하므로 동일 리스트 참조를 넘기지 않는다
        List<String> actions = afternote.getActions() == null
                ? new ArrayList<>()
                : new ArrayList<>(afternote.getActions());
        if ((storedCategory == AfternoteCategoryType.SOCIAL
                || storedCategory == AfternoteCategoryType.BUSINESS
                || storedCategory == AfternoteCategoryType.GALLERY)
                && writeRequest.getActions() != null) {
            actions = new ArrayList<>(writeRequest.getActions());
        }

        afternote.update(title, afternote.getSortOrder(), leaveMessage, actions, writeRequest.getIsDraft());

        // 관계 데이터 업데이트 (카테고리 전략 + 공통 receivers 처리)
        relationService.updateRelationsByCategory(afternote, writeRequest, storedCategory);
        
        return AfternoteCreateResponse.builder()
                .afternoteId(afternote.getId())
                .build();
    }

    @Transactional
    public void deleteAfternote(Long userId, Long afternoteId) {
        Afternote afternote = afternoteRepository.findById(afternoteId)
                .orElseThrow(() -> new CustomException(ErrorCode.AFTERNOTE_NOT_FOUND));
        if(!afternote.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.AFTERNOTE_ACCESS_DENIED);
        }
        afternoteRepository.delete(afternote);
        afternoteRepository.flush();
    }
}
