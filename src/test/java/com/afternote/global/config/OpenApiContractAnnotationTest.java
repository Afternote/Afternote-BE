package com.afternote.global.config;

import com.afternote.domain.afternote.controller.AfternoteController;
import com.afternote.domain.appversion.controller.AppVersionController;
import com.afternote.domain.appversion.dto.AppVersionCheckResponse;
import com.afternote.domain.music.controller.MusicController;
import com.afternote.domain.timeletter.dto.request.TimeLetterCreateRequest;
import com.afternote.domain.user.controller.UserController;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #123 회귀 방지: 배포 OpenAPI와 어긋나기 쉬운 계약을 어노테이션 단위로 고정한다.
 * (전체 /v3/api-docs 기동 없이 CI에서 빠르게 검증)
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
    @DisplayName("GET /afternotes/{id} 는 401·404를 OpenAPI에 선언한다")
    void afternoteDetail_Documents401And404() throws Exception {
        Method getDetail = AfternoteController.class.getDeclaredMethod(
                "getDetailAfternote",
                Long.class,
                Long.class
        );
        Set<String> codes = responseCodes(getDetail);

        assertThat(codes).contains("200", "401", "404");
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

    private static Set<String> responseCodes(Method method) {
        ApiResponses responses = method.getAnnotation(ApiResponses.class);
        assertThat(responses).as("@ApiResponses on %s", method.getName()).isNotNull();
        return Arrays.stream(responses.value())
                .map(ApiResponse::responseCode)
                .collect(Collectors.toSet());
    }
}
