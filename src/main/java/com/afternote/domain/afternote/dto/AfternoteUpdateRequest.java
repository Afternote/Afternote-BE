package com.afternote.domain.afternote.dto;

import com.afternote.domain.afternote.model.AfternoteCategoryType;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Getter;

import java.util.List;

/**
 * 애프터노트 수정(PATCH) 전용 요청.
 * 카테고리는 path의 afternoteId로 조회한 값이 SSOT이며, 생략 가능.
 * 기존 클라이언트 호환을 위해 동일 category는 허용하고, 다르면 400.
 */
@Schema(description = "애프터노트 수정 요청. 수정하지 않을 필드는 생략 가능. category는 생략 권장(변경 불가).")
public record AfternoteUpdateRequest(
        @Schema(
                description = "카테고리. 생략 가능. 보내면 기존과 같아야 하며, 다르면 400(1614)",
                example = "SOCIAL",
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Getter
        AfternoteCategoryType category,

        @Schema(description = "제목. 생략 시 기존 값 유지", example = "인스타그램", nullable = true)
        @Getter
        String title,

        @Schema(description = "체크리스트 (선택, SOCIAL/BUSINESS/GALLERY 전용). 생략 시 기존 값 유지", nullable = true)
        @Getter
        List<String> actions,

        @Schema(description = "남기실 말씀 블록 목록. 생략 시 기존 값 유지", nullable = true)
        @Getter
        @Valid
        List<LeaveMessageBlock> leaveMessage,

        @Schema(description = "계정 정보 (SOCIAL/BUSINESS). 생략 시 기존 값 유지. 정식 등록 상태면 요청·기존 합쳐 필수", nullable = true)
        @Getter
        AfternoteCreateRequest.CredentialsRequest credentials,

        @Schema(description = "수신자 목록. 생략 시 기존 값 유지. 포함 시 각 receiverId 필수", nullable = true)
        @Getter
        List<AfternoteCreateRequest.ReceiverRequest> receivers,

        @Schema(
                description = "플레이리스트 (PLAYLIST). playlist 객체 생략 시 플레이리스트 전체 유지. "
                        + "정식 등록 상태면 요청·기존 합쳐 필수. "
                        + "songs 생략(null)은 기존 곡 유지, 빈 배열 [] 은 전부 삭제(발행 노트는 1610). "
                        + "memorialPhotoUrl·memorialVideo·memorialAudioUrl 은 필드 생략 시 유지, "
                        + "JSON null 이면 해당 미디어를 삭제한다(DB 참조 제거 + S3 객체 삭제). "
                        + "값이 있으면 교체(업로드로 발급된 afternotes 키만 허용).",
                nullable = true
        )
        @Getter
        @JsonDeserialize(using = PlaylistRequestDeserializer.class)
        AfternoteCreateRequest.PlaylistRequest playlist,

        @Schema(
                description = "임시저장 여부. true면 credentials/playlist 필수 검증 완화. "
                        + "false(정식 등록)로 남거나 전환되면 카테고리별 필수값 검증. 생략 시 기존 값 유지",
                example = "false",
                nullable = true
        )
        @Getter
        Boolean isDraft
) {

    /**
     * 관계·검증 계층이 CreateRequest를 쓰므로, 저장 카테고리를 채워 변환한다.
     * (category 변경 검사는 변환 전에 UpdateRequest로 수행)
     */
    public AfternoteCreateRequest toWriteRequest(AfternoteCategoryType storedCategory) {
        return new AfternoteCreateRequest(
                storedCategory,
                title,
                actions,
                leaveMessage,
                credentials,
                receivers,
                playlist,
                isDraft
        );
    }
}
