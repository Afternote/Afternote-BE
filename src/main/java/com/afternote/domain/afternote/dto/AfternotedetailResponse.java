package com.afternote.domain.afternote.dto;

import com.afternote.domain.afternote.model.AfternoteCategoryType;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 애프터노트 상세 응답. 임시저장과 발행 완료(일반 / PLAYLIST)를 타입으로 구분한다.
 */
@Schema(
        description = "애프터노트 상세 응답. isDraft와 category에 따라 필수 필드가 다르다.",
        oneOf = {
                AfternotedetailResponse.Draft.class,
                AfternotedetailResponse.Published.class,
                AfternotedetailResponse.PublishedPlaylist.class
        }
)
public sealed interface AfternotedetailResponse permits
        AfternotedetailResponse.Draft,
        AfternotedetailResponse.Published,
        AfternotedetailResponse.PublishedPlaylist {

    String ACTIONS_DESCRIPTION = "체크리스트 (SOCIAL/BUSINESS/GALLERY 전용). PLAYLIST는 null";
    String LEAVE_MESSAGE_DESCRIPTION = "남기실 말씀 블록 목록 (제목+본문). 미작성·GALLERY 임시저장 등 일부 경우 null";
    String CREDENTIALS_DESCRIPTION = "계정 정보 (SOCIAL/BUSINESS 전용). GALLERY/PLAYLIST는 null";
    String DRAFT_PLAYLIST_DESCRIPTION =
            "플레이리스트 정보 (PLAYLIST 전용). 그 외 카테고리는 null. 임시저장 PLAYLIST는 미작성 시 null";
    String PUBLISHED_PLAYLIST_OPTIONAL_DESCRIPTION =
            "플레이리스트 정보 (PLAYLIST 전용). SOCIAL/BUSINESS/GALLERY 발행 상세는 null";

    Long getAfternoteId();

    AfternoteCategoryType getCategory();

    String getTitle();

    Boolean getIsDraft();

    List<String> getActions();

    List<LeaveMessageBlock> getLeaveMessage();

    AfternoteCreateRequest.CredentialsRequest getCredentials();

    List<AfternoteReceiverResponse> getReceivers();

    LocalDateTime getUpdatedAt();

    static AfternotedetailResponse of(
            Long afternoteId,
            AfternoteCategoryType category,
            String title,
            boolean draft,
            List<String> actions,
            List<LeaveMessageBlock> leaveMessage,
            AfternoteCreateRequest.CredentialsRequest credentials,
            List<AfternoteReceiverResponse> receivers,
            AfternoteCreateRequest.PlaylistRequest playlist,
            LocalDateTime updatedAt
    ) {
        if (draft) {
            return new Draft(
                    afternoteId,
                    category,
                    title,
                    true,
                    actions,
                    leaveMessage,
                    credentials,
                    receivers,
                    playlist,
                    updatedAt
            );
        }
        if (category == AfternoteCategoryType.PLAYLIST) {
            requirePublishedPlaylist(playlist);
            return new PublishedPlaylist(
                    afternoteId,
                    AfternoteCategoryType.PLAYLIST,
                    title,
                    false,
                    null,
                    leaveMessage,
                    null,
                    receivers,
                    AfternotePublishedPlaylistResponse.from(playlist),
                    updatedAt
            );
        }
        if (category == AfternoteCategoryType.SOCIAL || category == AfternoteCategoryType.BUSINESS) {
            requirePublishedCredentials(credentials);
        }
        return new Published(
                afternoteId,
                category,
                title,
                false,
                actions,
                leaveMessage,
                credentials,
                receivers,
                playlist,
                updatedAt
        );
    }

    private static void requirePublishedPlaylist(AfternoteCreateRequest.PlaylistRequest playlist) {
        if (playlist == null) {
            throw new CustomException(ErrorCode.PLAYLIST_REQUIRED);
        }
        if (playlist.getSongs() == null || playlist.getSongs().isEmpty()) {
            throw new CustomException(ErrorCode.PLAYLIST_SONGS_REQUIRED);
        }
    }

    private static void requirePublishedCredentials(AfternoteCreateRequest.CredentialsRequest credentials) {
        if (credentials == null) {
            throw new CustomException(ErrorCode.SOCIAL_CREDENTIALS_REQUIRED);
        }
        if (credentials.getId() == null || credentials.getId().isBlank()) {
            throw new CustomException(ErrorCode.SOCIAL_ACCOUNT_ID_REQUIRED);
        }
        if (credentials.getPassword() == null || credentials.getPassword().isBlank()) {
            throw new CustomException(ErrorCode.SOCIAL_ACCOUNT_PASSWORD_REQUIRED);
        }
    }

    @Schema(
            name = "AfternoteDraftDetailResponse",
            description = "임시저장 상세. PLAYLIST의 playlist와 SOCIAL/BUSINESS의 credentials는 미작성 시 null"
    )
    record Draft(
            @Schema(description = "애프터노트 아이디", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @Getter Long afternoteId,
            @Schema(description = "카테고리", example = "PLAYLIST", requiredMode = Schema.RequiredMode.REQUIRED)
            @Getter AfternoteCategoryType category,
            @Schema(description = "제목", example = "인스타그램", requiredMode = Schema.RequiredMode.REQUIRED)
            @Getter String title,
            @Schema(
                    description = "임시저장 여부. 임시저장 상세는 항상 true",
                    example = "true",
                    allowableValues = "true",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            @Getter Boolean isDraft,
            @Schema(
                    description = ACTIONS_DESCRIPTION,
                    nullable = true,
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED
            )
            @Getter List<String> actions,
            @Schema(
                    description = LEAVE_MESSAGE_DESCRIPTION,
                    nullable = true,
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED
            )
            @Getter List<LeaveMessageBlock> leaveMessage,
            @Schema(
                    description = CREDENTIALS_DESCRIPTION,
                    nullable = true,
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                    allOf = AfternoteCreateRequest.CredentialsRequest.class
            )
            @Getter AfternoteCreateRequest.CredentialsRequest credentials,
            @Schema(description = "수신자 목록 (receiverId, name, relation)", requiredMode = Schema.RequiredMode.REQUIRED)
            @Getter List<AfternoteReceiverResponse> receivers,
            @Schema(
                    description = DRAFT_PLAYLIST_DESCRIPTION,
                    nullable = true,
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                    allOf = AfternoteCreateRequest.PlaylistRequest.class
            )
            @Getter AfternoteCreateRequest.PlaylistRequest playlist,
            @Schema(description = "최종 수정일", requiredMode = Schema.RequiredMode.REQUIRED)
            @Getter LocalDateTime updatedAt
    ) implements AfternotedetailResponse {
    }

    @Schema(
            name = "AfternotePublishedDetailResponse",
            description = "발행 완료 상세 (SOCIAL/BUSINESS/GALLERY). PLAYLIST 발행은 AfternotePublishedPlaylistDetailResponse"
    )
    record Published(
            @Schema(description = "애프터노트 아이디", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @Getter Long afternoteId,
            @Schema(description = "카테고리", example = "SOCIAL", requiredMode = Schema.RequiredMode.REQUIRED)
            @Getter AfternoteCategoryType category,
            @Schema(description = "제목", example = "인스타그램", requiredMode = Schema.RequiredMode.REQUIRED)
            @Getter String title,
            @Schema(
                    description = "임시저장 여부. 발행 완료 상세는 항상 false",
                    example = "false",
                    allowableValues = "false",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            @Getter Boolean isDraft,
            @Schema(
                    description = ACTIONS_DESCRIPTION,
                    nullable = true,
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED
            )
            @Getter List<String> actions,
            @Schema(
                    description = LEAVE_MESSAGE_DESCRIPTION,
                    nullable = true,
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED
            )
            @Getter List<LeaveMessageBlock> leaveMessage,
            @Schema(
                    description = CREDENTIALS_DESCRIPTION,
                    nullable = true,
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                    allOf = AfternoteCreateRequest.CredentialsRequest.class
            )
            @Getter AfternoteCreateRequest.CredentialsRequest credentials,
            @Schema(description = "수신자 목록 (receiverId, name, relation)", requiredMode = Schema.RequiredMode.REQUIRED)
            @Getter List<AfternoteReceiverResponse> receivers,
            @Schema(
                    description = PUBLISHED_PLAYLIST_OPTIONAL_DESCRIPTION,
                    nullable = true,
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                    allOf = AfternoteCreateRequest.PlaylistRequest.class
            )
            @Getter AfternoteCreateRequest.PlaylistRequest playlist,
            @Schema(description = "최종 수정일", requiredMode = Schema.RequiredMode.REQUIRED)
            @Getter LocalDateTime updatedAt
    ) implements AfternotedetailResponse {
    }

    @Schema(
            name = "AfternotePublishedPlaylistDetailResponse",
            description = "발행 완료 PLAYLIST 상세. playlist와 songs는 필수·non-null"
    )
    record PublishedPlaylist(
            @Schema(description = "애프터노트 아이디", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @Getter Long afternoteId,
            @Schema(
                    description = "카테고리. 발행 완료 PLAYLIST 상세는 항상 PLAYLIST",
                    example = "PLAYLIST",
                    allowableValues = "PLAYLIST",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            @Getter AfternoteCategoryType category,
            @Schema(description = "제목", example = "추억", requiredMode = Schema.RequiredMode.REQUIRED)
            @Getter String title,
            @Schema(
                    description = "임시저장 여부. 발행 완료 상세는 항상 false",
                    example = "false",
                    allowableValues = "false",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            @Getter Boolean isDraft,
            @Schema(
                    description = ACTIONS_DESCRIPTION,
                    nullable = true,
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED
            )
            @Getter List<String> actions,
            @Schema(
                    description = LEAVE_MESSAGE_DESCRIPTION,
                    nullable = true,
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED
            )
            @Getter List<LeaveMessageBlock> leaveMessage,
            @Schema(
                    description = CREDENTIALS_DESCRIPTION,
                    nullable = true,
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                    allOf = AfternoteCreateRequest.CredentialsRequest.class
            )
            @Getter AfternoteCreateRequest.CredentialsRequest credentials,
            @Schema(description = "수신자 목록 (receiverId, name, relation)", requiredMode = Schema.RequiredMode.REQUIRED)
            @Getter List<AfternoteReceiverResponse> receivers,
            @Schema(
                    description = "플레이리스트 정보. 발행 완료 PLAYLIST는 필수이며 최소 1곡",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            @Getter AfternotePublishedPlaylistResponse playlist,
            @Schema(description = "최종 수정일", requiredMode = Schema.RequiredMode.REQUIRED)
            @Getter LocalDateTime updatedAt
    ) implements AfternotedetailResponse {
    }
}
