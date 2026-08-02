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
        @Schema(description = "애프터노트 ID", example = "1")
        @Getter
        Long id,

        @Schema(description = "카테고리", example = "PLAYLIST")
        @Getter
        AfternoteCategoryType category,

        @Schema(description = "제목", example = "내 아들에게")
        @Getter
        String title,

        @Schema(description = "체크리스트 (SOCIAL/GALLERY 전용)")
        @Getter
        List<String> actions,

        @Schema(description = "남기실 말씀 블록 목록 (제목+본문)")
        @Getter
        List<LeaveMessageBlock> leaveMessage,

        @Schema(description = "발신자 이름", example = "김철수")
        @Getter
        String senderName,

        @Schema(description = "작성 시간")
        @Getter
        LocalDateTime createdAt,

        @Schema(description = "플레이리스트 정보 (PLAYLIST 전용)")
        @Getter
        PlaylistInfo playlist
) {









    @Builder
    public static record PlaylistInfo(
            @Schema(description = "분위기 설명")
            @Getter
            String atmosphere,

            @Schema(description = "노래 목록")
            @Getter
            List<SongInfo> songs,

            @Schema(description = "추모 영상")
            @Getter
            MemorialVideoInfo memorialVideo
    ) {


    }

    @Builder
    public static record SongInfo(
            @Schema(description = "곡 제목")
            @Getter
            String title,

            @Schema(description = "아티스트")
            @Getter
            String artist,

            @Schema(description = "커버 이미지 URL")
            @Getter
            String coverUrl
    ) {


    }

    @Builder
    public static record MemorialVideoInfo(
            @Schema(description = "영상 URL")
            @Getter
            String videoUrl,

            @Schema(description = "썸네일 URL")
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
                    .songs(songs)
                    .memorialVideo(video)
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
