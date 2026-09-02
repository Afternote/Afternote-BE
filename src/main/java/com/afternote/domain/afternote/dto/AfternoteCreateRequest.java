package com.afternote.domain.afternote.dto;

import com.afternote.domain.afternote.model.AfternoteCategoryType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.List;

public record AfternoteCreateRequest(
        @NotNull
        @Schema(description = "카테고리", example = "SOCIAL")
        @Getter
        AfternoteCategoryType category,

        @NotBlank
        @Schema(description = "제목", example = "인스타그램")
        @Getter
        String title,

        @Schema(description = "체크리스트 (선택, SOCIAL/BUSINESS/GALLERY 전용). 생략 또는 빈 배열 가능")
        @Getter
        List<String> actions,

        @Schema(description = "남기실 말씀 블록 목록 (선택, 모든 카테고리). 생략 가능")
        @Getter
        @Valid
        List<LeaveMessageBlock> leaveMessage,

        @Schema(description = "계정 정보 (SOCIAL/BUSINESS 전용, 정식 등록 시 필수)")
        @Getter
        CredentialsRequest credentials,

        @Schema(description = "수신자 목록 (선택, 모든 카테고리). 생략 또는 빈 배열 가능. 포함 시 각 receiverId 필수")
        @Getter
        List<ReceiverRequest> receivers,

        @Schema(description = "플레이리스트 정보 (Playlist 전용)")
        @Getter
        PlaylistRequest playlist,

        @Schema(
                description = "임시저장 여부. true면 credentials/playlist 등 필수 검증 완화(느슨). "
                        + "false 또는 생략 시 정식 등록으로 보고 카테고리별 필수값 검증(타이트)",
                example = "false"
        )
        @Getter
        Boolean isDraft
) {

    public boolean isDraftValue() {
        return Boolean.TRUE.equals(isDraft);
    }

    

    
    
    
    
    

    public static record CredentialsRequest(
            @Schema(description = "아이디", example = "my_insta_id")
            @Getter
            String id,

            @Schema(description = "비밀번호", example = "password123")
            @Getter
            String password
    ) {
        
    }

    public static record ReceiverRequest(
            @Schema(description = "수신자 ID", example = "1")
            @Getter
            Long receiverId
    ) {
    }

    public static record PlaylistRequest(
            @Schema(description = "분위기 설명", example = "차분하고 조용하게 보내주세요.")
            @Getter
            String atmosphere,

            @Schema(
                    description = PATCH_MEDIA_DESCRIPTION_PREFIX + "영정 사진 URL. 생성 시 생략/null 이면 없음.",
                    nullable = true
            )
            @Getter
            String memorialPhotoUrl,

            @ArraySchema(arraySchema = @Schema(
                    description = "노래 목록. PATCH: 필드 생략(null) 시 기존 곡 유지, 빈 배열 [] 은 전부 삭제(발행 노트는 1610). "
                            + "생성 정식 등록이면 1곡 이상 필수.",
                    nullable = true
            ))
            @Getter
            List<SongRequest> songs,

            @Schema(
                    description = PATCH_MEDIA_DESCRIPTION_PREFIX + "추모 영상. 생성 시 생략/null 이면 없음. "
                            + "PATCH에서 null 이면 영상·썸네일을 함께 삭제한다.",
                    nullable = true
            )
            @Getter
            MemorialVideoRequest memorialVideo,

            @Schema(
                    description = PATCH_MEDIA_DESCRIPTION_PREFIX + "추모 음성 URL. 생성 시 생략/null 이면 없음. "
                            + "플레이리스트당 1개(mp3/m4a/wav).",
                    nullable = true
            )
            @Getter
            String memorialAudioUrl,

            @JsonIgnore
            @Schema(hidden = true)
            boolean memorialPhotoUrlSpecified,

            @JsonIgnore
            @Schema(hidden = true)
            boolean memorialVideoSpecified,

            @JsonIgnore
            @Schema(hidden = true)
            boolean memorialAudioUrlSpecified
    ) {

        static final String PATCH_MEDIA_DESCRIPTION_PREFIX =
                "PATCH: 필드 생략 시 유지, JSON null 이면 삭제(DB·S3). ";

        @JsonIgnore
        public PlaylistRequest(
                String atmosphere,
                String memorialPhotoUrl,
                List<SongRequest> songs,
                MemorialVideoRequest memorialVideo,
                String memorialAudioUrl
        ) {
            this(
                    atmosphere,
                    memorialPhotoUrl,
                    songs,
                    memorialVideo,
                    memorialAudioUrl,
                    memorialPhotoUrl != null,
                    memorialVideo != null,
                    memorialAudioUrl != null
            );
        }

        public static PlaylistRequest parsed(
                String atmosphere,
                String memorialPhotoUrl,
                List<SongRequest> songs,
                MemorialVideoRequest memorialVideo,
                String memorialAudioUrl,
                boolean memorialPhotoUrlSpecified,
                boolean memorialVideoSpecified,
                boolean memorialAudioUrlSpecified
        ) {
            return new PlaylistRequest(
                    atmosphere,
                    memorialPhotoUrl,
                    songs,
                    memorialVideo,
                    memorialAudioUrl,
                    memorialPhotoUrlSpecified,
                    memorialVideoSpecified,
                    memorialAudioUrlSpecified
            );
        }
    }

    public static record SongRequest(
            @Schema(description = "곡 제목", example = "보고싶다")
            @Getter
            String title,

            @Schema(description = "아티스트", example = "김범수")
            @Getter
            String artist,

            @Schema(description = "커버 이미지 URL. 있으면 업로드로 발급된 afternotes 이미지 키만 허용", nullable = true)
            @Getter
            String coverUrl
    ) {
        
        
    }

    public static record MemorialVideoRequest(
            @Schema(description = "영상 URL. 업로드로 발급된 afternotes 영상(mp4/mov) 키만 허용")
            @Getter
            String videoUrl,

            @Schema(description = "썸네일 URL. 있으면 업로드로 발급된 afternotes 이미지 키만 허용", nullable = true)
            @Getter
            String thumbnailUrl
    ) {
    }
}
