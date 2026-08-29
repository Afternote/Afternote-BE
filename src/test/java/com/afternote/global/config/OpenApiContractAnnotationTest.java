package com.afternote.global.config;

import com.afternote.domain.afternote.controller.AfternoteController;
import com.afternote.domain.afternote.dto.AfternotedetailResponse;
import com.afternote.domain.afternote.dto.AfternoteUpdateRequest;
import com.afternote.domain.appversion.controller.AppVersionController;
import com.afternote.domain.appversion.dto.AppVersionCheckResponse;
import com.afternote.domain.diary.dto.DiaryCreateRequest;
import com.afternote.domain.music.controller.MusicController;
import com.afternote.domain.timeletter.dto.request.TimeLetterCreateRequest;
import com.afternote.domain.timeletter.dto.request.TimeLetterUpdateRequest;
import com.afternote.domain.user.controller.UserController;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #123·#255 회귀 방지: 배포 OpenAPI와 어긋나기 쉬운 계약을 어노테이션 단위로 고정한다.
 * $ref 형제 속성 유실은 {@link OpenApiGeneratedDocsTest} 가 생성 문서로 검증한다.
 *
 * 주의: {@code @Operation(security = {})} 는 springdoc가 생략해서 전역 bearer가 남는다.
 * 공개 API는 반드시 {@code @SecurityRequirements} 로 끊는다.
 */
class OpenApiContractAnnotationTest {

    @Test
    @DisplayName("공개 API는 @SecurityRequirements 로 전역 Bearer 상속을 끊는다")
    void publicApis_OverrideSecurityToEmpty() throws Exception {
        Method checkVersion = AppVersionController.class.getDeclaredMethod(
                "checkVersion",
                com.afternote.domain.appversion.model.AppPlatform.class,
                int.class
        );
        Method searchMusic = MusicController.class.getDeclaredMethod("searchMusic", String.class);

        assertThat(checkVersion.getAnnotation(SecurityRequirements.class)).isNotNull();
        assertThat(searchMusic.getAnnotation(SecurityRequirements.class)).isNotNull();
        assertThat(checkVersion.getAnnotation(SecurityRequirements.class).value()).isEmpty();
        assertThat(searchMusic.getAnnotation(SecurityRequirements.class).value()).isEmpty();
    }

    @Test
    @DisplayName("GET /users/me 는 401을 OpenAPI에 선언한다")
    void usersMe_Documents401() throws Exception {
        Method getMyProfile = UserController.class.getDeclaredMethod("getMyProfile", Long.class);
        Set<String> codes = responseCodes(getMyProfile);

        assertThat(codes).contains("200", "401");
    }

    @Test
    @DisplayName("GET /afternotes/{id} 는 400·401·404를 OpenAPI에 선언한다")
    void afternoteDetail_Documents401And404() throws Exception {
        Method getDetail = AfternoteController.class.getDeclaredMethod(
                "getDetailAfternote",
                Long.class,
                Long.class
        );
        Set<String> codes = responseCodes(getDetail);

        assertThat(codes).contains("200", "400", "401", "404");
    }

    @Test
    @DisplayName("AppVersionCheckResponse.storeUrl 은 nullable=true")
    void storeUrl_IsNullableInSchema() throws Exception {
        // record component 헤더의 @Schema는 accessor 메서드에 붙는다
        Schema schema = AppVersionCheckResponse.class.getDeclaredMethod("storeUrl").getAnnotation(Schema.class);
        assertThat(schema).isNotNull();
        assertThat(schema.nullable()).isTrue();
    }

    @Test
    @DisplayName("TimeLetter DRAFT의 receiverIds는 OpenAPI에서 선택 입력이다")
    void timeLetterDraftReceiverIds_AreOptionalInSchema() throws Exception {
        Schema schema = TimeLetterCreateRequest.class.getDeclaredMethod("receiverIds").getAnnotation(Schema.class);

        assertThat(schema).isNotNull();
        assertThat(schema.nullable()).isTrue();
        assertThat(schema.requiredMode()).isEqualTo(Schema.RequiredMode.NOT_REQUIRED);
        assertThat(schema.description()).contains("DRAFT는 생략하거나 빈 목록 가능");
    }

    @Test
    @DisplayName("임시저장 상세는 참조형 필드를 allOf+$ref 로 nullable 표기한다")
    void afternoteDraftDetail_DocumentsNullableObjectFieldsWithAllOf() throws Exception {
        for (String field : Set.of("afternoteId", "category", "title", "isDraft", "receivers", "updatedAt")) {
            Schema schema = AfternotedetailResponse.Draft.class.getDeclaredMethod(field).getAnnotation(Schema.class);
            assertThat(schema).as("@Schema on Draft.%s", field).isNotNull();
            assertThat(schema.requiredMode()).isEqualTo(Schema.RequiredMode.REQUIRED);
        }

        Schema actions = AfternotedetailResponse.Draft.class.getDeclaredMethod("actions").getAnnotation(Schema.class);
        assertThat(actions.nullable()).isTrue();
        assertThat(actions.requiredMode()).isEqualTo(Schema.RequiredMode.NOT_REQUIRED);
        assertThat(actions.description()).contains("SOCIAL/BUSINESS/GALLERY").contains("PLAYLIST");

        Schema credentials = AfternotedetailResponse.Draft.class.getDeclaredMethod("credentials").getAnnotation(Schema.class);
        assertThat(credentials.nullable()).isTrue();
        assertThat(credentials.requiredMode()).isEqualTo(Schema.RequiredMode.NOT_REQUIRED);
        assertThat(credentials.description()).contains("SOCIAL/BUSINESS");
        assertThat(credentials.allOf()).containsExactly(com.afternote.domain.afternote.dto.AfternoteCreateRequest.CredentialsRequest.class);

        Schema playlist = AfternotedetailResponse.Draft.class.getDeclaredMethod("playlist").getAnnotation(Schema.class);
        assertThat(playlist.nullable()).isTrue();
        assertThat(playlist.requiredMode()).isEqualTo(Schema.RequiredMode.NOT_REQUIRED);
        assertThat(playlist.allOf()).containsExactly(com.afternote.domain.afternote.dto.AfternoteCreateRequest.PlaylistRequest.class);
    }

    @Test
    @DisplayName("발행 완료 PLAYLIST 상세는 playlist를 required·non-null로 둔다")
    void afternotePublishedPlaylistDetail_RequiresPlaylist() throws Exception {
        Schema playlist = AfternotedetailResponse.PublishedPlaylist.class
                .getDeclaredMethod("playlist")
                .getAnnotation(Schema.class);
        assertThat(playlist.nullable()).isFalse();
        assertThat(playlist.requiredMode()).isEqualTo(Schema.RequiredMode.REQUIRED);
        assertThat(playlist.allOf()).isEmpty();

        io.swagger.v3.oas.annotations.media.ArraySchema songsArray =
                com.afternote.domain.afternote.dto.AfternotePublishedPlaylistResponse.class
                .getDeclaredMethod("songs")
                .getAnnotation(io.swagger.v3.oas.annotations.media.ArraySchema.class);
        Schema songs = com.afternote.domain.afternote.dto.AfternotePublishedPlaylistResponse.class
                .getDeclaredMethod("songs")
                .getAnnotation(Schema.class);
        assertThat(songsArray).isNotNull();
        assertThat(songsArray.minItems()).isEqualTo(1);
        assertThat(songs).isNotNull();
        assertThat(songs.requiredMode()).isEqualTo(Schema.RequiredMode.REQUIRED);
    }

    @Test
    @DisplayName("AfternoteUpdateRequest.category 는 OpenAPI에서 선택 입력이다")
    void afternoteUpdateCategory_IsOptionalInSchema() throws Exception {
        Schema schema = AfternoteUpdateRequest.class.getDeclaredMethod("category").getAnnotation(Schema.class);

        assertThat(schema).isNotNull();
        assertThat(schema.nullable()).isTrue();
        assertThat(schema.requiredMode()).isEqualTo(Schema.RequiredMode.NOT_REQUIRED);
        assertThat(schema.description()).contains("생략");
    }

    @Test
    @DisplayName("Diary 임시저장의 title·content·todayMood는 OpenAPI에서 선택 입력이다")
    void diaryDraftFormalFields_AreOptionalInSchema() throws Exception {
        for (String field : Set.of("title", "content", "todayMood")) {
            Schema schema = DiaryCreateRequest.class.getDeclaredMethod(field).getAnnotation(Schema.class);
            assertThat(schema).as("@Schema on DiaryCreateRequest.%s", field).isNotNull();
            assertThat(schema.nullable()).isTrue();
            assertThat(schema.requiredMode()).isEqualTo(Schema.RequiredMode.NOT_REQUIRED);
            assertThat(schema.description()).contains("임시저장").contains("정식 등록");
        }
    }

    @Test
    @DisplayName("TimeLetter 생성·수정 sendAt은 OffsetDateTime과 Jackson 기본 역직렬화를 사용한다")
    void timeLetterSendAt_DocumentsAndUsesTheSameInputContract() throws Exception {
        for (Class<?> requestType : Set.of(TimeLetterCreateRequest.class, TimeLetterUpdateRequest.class)) {
            Method sendAt = requestType.getDeclaredMethod("sendAt");
            Schema schema = sendAt.getAnnotation(Schema.class);
            JsonDeserialize jsonDeserialize = sendAt.getAnnotation(JsonDeserialize.class);

            assertThat(schema).as("@Schema on %s.sendAt", requestType.getSimpleName()).isNotNull();
            assertThat(schema.type()).isEqualTo("string");
            assertThat(schema.format()).isEqualTo("date-time");
            assertThat(schema.example()).endsWith("+09:00");
            assertThat(schema.description())
                    .contains("UTC 오프셋 포함")
                    .doesNotContain("RFC 3339");
            assertThat(sendAt.getReturnType()).isEqualTo(OffsetDateTime.class);
            assertThat(jsonDeserialize)
                    .as("@JsonDeserialize on %s.sendAt", requestType.getSimpleName())
                    .isNull();
        }
    }

    private static Set<String> responseCodes(Method method) {
        ApiResponses responses = method.getAnnotation(ApiResponses.class);
        assertThat(responses).as("@ApiResponses on %s", method.getName()).isNotNull();
        return Arrays.stream(responses.value())
                .map(ApiResponse::responseCode)
                .collect(Collectors.toSet());
    }
}
