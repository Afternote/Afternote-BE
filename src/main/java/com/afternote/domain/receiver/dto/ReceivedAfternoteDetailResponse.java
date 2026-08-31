package com.afternote.domain.receiver.dto;

import com.afternote.domain.afternote.dto.LeaveMessageBlock;
import com.afternote.domain.afternote.model.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

@Schema(description = "수신한 애프터노트 상세 응답")
@Builder
public record ReceivedAfternoteDetailResponse(
        @Schema(description = "애프터노트 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @Getter
        Long id,

        @Schema(description = "카테고리", example = "PLAYLIST", requiredMode = Schema.RequiredMode.REQUIRED)
        @Getter
        AfternoteCategoryType category,

        @Schema(description = "제목", example = "내 아들에게", requiredMode = Schema.RequiredMode.REQUIRED)
        @Getter
        String title,

        @Schema(
                description = ACTIONS_DESCRIPTION,
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Getter
        List<String> actions,

        @Schema(
                description = LEAVE_MESSAGE_DESCRIPTION,
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Getter
        List<LeaveMessageBlock> leaveMessage,

        @Schema(description = "발신자 이름", example = "김철수", requiredMode = Schema.RequiredMode.REQUIRED)
        @Getter
        String senderName,

        @Schema(
                description = "작성 시간. 오프셋 없는 ISO-8601 로컬 시각(Asia/Seoul)",
                example = ReceivedRecordBoxResponse.LOCAL_DATE_TIME_EXAMPLE,
                type = "string",
                format = ReceivedRecordBoxResponse.LOCAL_DATE_TIME_FORMAT,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @Getter
        LocalDateTime createdAt,

        @Schema(
                description = PLAYLIST_DESCRIPTION,
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                allOf = PlaylistInfo.class
        )
        @Getter
        PlaylistInfo playlist
) {
    public static final String ACTIONS_DESCRIPTION =
            "체크리스트 (SOCIAL/BUSINESS/GALLERY 전용). PLAYLIST는 null";
    public static final String LEAVE_MESSAGE_DESCRIPTION =
            "남기실 말씀 블록 목록 (제목+본문). 미작성이면 null";
    public static final String PLAYLIST_DESCRIPTION =
            "플레이리스트 정보 (PLAYLIST 전용). 그 외 카테고리이거나 미작성이면 null";

    @Builder
    @Schema(name = "ReceivedPlaylistInfo", description = "수신 애프터노트 플레이리스트")
    public static record PlaylistInfo(
            @Schema(
                    description = "분위기 설명. 미작성이면 null",
                    nullable = true,
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED
            )
            @Getter
            String atmosphere,

            @Schema(
                    description = "영정 사진 URL. 없으면 null",
                    nullable = true,
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED
            )
            @Getter
            String memorialPhotoUrl,

            @Schema(description = "노래 목록", requiredMode = Schema.RequiredMode.REQUIRED)
            @Getter
            List<SongInfo> songs,

            @Schema(
                    description = "추모 영상. 없으면 null",
                    nullable = true,
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                    allOf = MemorialVideoInfo.class
            )
            @Getter
            MemorialVideoInfo memorialVideo,

            @Schema(
                    description = "추모 음성 URL. 없으면 null",
                    nullable = true,
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED
            )
            @Getter
            String memorialAudioUrl
    ) {
    }

    @Builder
    @Schema(name = "ReceivedSongInfo", description = "수신 애프터노트 곡")
    public static record SongInfo(
            @Schema(description = "곡 제목", requiredMode = Schema.RequiredMode.REQUIRED)
            @Getter
            String title,

            @Schema(description = "아티스트", requiredMode = Schema.RequiredMode.REQUIRED)
            @Getter
            String artist,

            @Schema(
                    description = "커버 이미지 URL. 없으면 null",
                    nullable = true,
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED
            )
            @Getter
            String coverUrl
    ) {
    }

    @Builder
    @Schema(name = "ReceivedMemorialVideoInfo", description = "수신 애프터노트 추모 영상")
    public static record MemorialVideoInfo(
            @Schema(
                    description = "영상 URL. 없으면 null",
                    nullable = true,
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED
            )
            @Getter
            String videoUrl,

            @Schema(
                    description = "썸네일 URL. 없으면 null",
                    nullable = true,
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED
            )
            @Getter
            String thumbnailUrl
    ) {
    }

    public static ReceivedAfternoteDetailResponse fromSocial(Afternote afternote, String senderName) {
        return ReceivedAfternoteDetailResponse.builder()
                .id(afternote.getId())
                .category(afternote.getCategoryType())
                .title(afternote.getTitle())
                .actions(afternote.getActions())
                .leaveMessage(afternote.getLeaveMessage())
                .senderName(senderName)
                .createdAt(afternote.getCreatedAt())
                .build();
    }

    public static ReceivedAfternoteDetailResponse fromGallery(Afternote afternote, String senderName) {
        return ReceivedAfternoteDetailResponse.builder()
                .id(afternote.getId())
                .category(afternote.getCategoryType())
                .title(afternote.getTitle())
                .actions(afternote.getActions())
                .leaveMessage(afternote.getLeaveMessage())
                .senderName(senderName)
                .createdAt(afternote.getCreatedAt())
                .build();
    }

    public static ReceivedAfternoteDetailResponse fromPlaylist(Afternote afternote, String senderName) {
        return fromPlaylist(afternote, senderName, Function.identity());
    }

    public static ReceivedAfternoteDetailResponse fromPlaylist(Afternote afternote, String senderName,
                                                               Function<String, String> urlResolver) {
        PlaylistInfo playlistInfo = null;

        if (afternote.getPlaylist() != null) {
            AfternotePlaylist pl = afternote.getPlaylist();

            List<SongInfo> songs = pl.getItems().stream()
                    .map(item -> SongInfo.builder()
                            .title(item.getSongTitle())
                            .artist(item.getArtist())
                            .coverUrl(urlResolver.apply(item.getCoverUrl()))
                            .build())
                    .toList();

            MemorialVideoInfo video = null;
            if (pl.getMemorialVideo() != null) {
                video = MemorialVideoInfo.builder()
                        .videoUrl(urlResolver.apply(pl.getMemorialVideo().getVideoUrl()))
                        .thumbnailUrl(urlResolver.apply(pl.getMemorialVideo().getThumbnailUrl()))
                        .build();
            }

            playlistInfo = PlaylistInfo.builder()
                    .atmosphere(pl.getAtmosphere())
                    .memorialPhotoUrl(urlResolver.apply(pl.getMemorialPhotoUrl()))
                    .songs(songs)
                    .memorialVideo(video)
                    .memorialAudioUrl(urlResolver.apply(pl.getMemorialAudioUrl()))
                    .build();
        }

        return ReceivedAfternoteDetailResponse.builder()
                .id(afternote.getId())
                .category(afternote.getCategoryType())
                .title(afternote.getTitle())
                .leaveMessage(afternote.getLeaveMessage())
                .senderName(senderName)
                .createdAt(afternote.getCreatedAt())
                .playlist(playlistInfo)
                .build();
    }
}
