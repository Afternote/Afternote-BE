package com.afternote.domain.afternote.dto;

import com.afternote.domain.afternote.model.AfternoteCategoryType;
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

            @Schema(description = "영정 사진 URL")
            @Getter
            String memorialPhotoUrl,

            @Schema(description = "노래 목록")
            @Getter
            List<SongRequest> songs,

            @Schema(description = "추모 영상")
            @Getter
            MemorialVideoRequest memorialVideo
    ) {
        
        
        
    }

    public static record SongRequest(
            @Schema(description = "곡 제목", example = "보고싶다")
            @Getter
            String title,

            @Schema(description = "아티스트", example = "김범수")
            @Getter
            String artist,

            @Schema(description = "커버 이미지 URL")
            @Getter
            String coverUrl
    ) {
        
        
    }

    public static record MemorialVideoRequest(
            @Schema(description = "영상 URL")
            @Getter
            String videoUrl,

            @Schema(description = "썸네일 URL")
            @Getter
            String thumbnailUrl
    ) {
        
    }
}
