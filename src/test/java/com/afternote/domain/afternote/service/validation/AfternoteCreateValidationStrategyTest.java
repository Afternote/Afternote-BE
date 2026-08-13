package com.afternote.domain.afternote.service.validation;

import com.afternote.domain.afternote.dto.AfternoteCreateRequest;
import com.afternote.domain.afternote.model.AfternoteCategoryType;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AfternoteCreateValidationStrategyTest {

    private final SocialValidationStrategy social = new SocialValidationStrategy();
    private final BusinessValidationStrategy business = new BusinessValidationStrategy();
    private final GalleryValidationStrategy gallery = new GalleryValidationStrategy();
    private final PlaylistValidationStrategy playlist = new PlaylistValidationStrategy();

    @Test
    @DisplayName("SOCIAL 생성 - receivers·actions 없어도 성공")
    void social_WithoutReceiversAndActions_Ok() {
        AfternoteCreateRequest request = new AfternoteCreateRequest(
                AfternoteCategoryType.SOCIAL,
                "인스타그램",
                null,
                null,
                new AfternoteCreateRequest.CredentialsRequest("id", "pw"),
                null,
                null,
                null
        );

        assertThatCode(() -> social.validateCreate(request)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SOCIAL 임시저장 - credentials 없어도 성공")
    void social_DraftWithoutCredentials_Ok() {
        AfternoteCreateRequest request = new AfternoteCreateRequest(
                AfternoteCategoryType.SOCIAL,
                "인스타그램",
                null,
                null,
                null,
                null,
                null,
                true
        );

        assertThatCode(() -> social.validateCreate(request)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SOCIAL 생성 - playlist 포함 시 1616, playlist 필드 기준 메시지")
    void social_CreateWithPlaylist_RejectsWithFieldMessage() {
        AfternoteCreateRequest request = new AfternoteCreateRequest(
                AfternoteCategoryType.SOCIAL,
                "인스타그램",
                null,
                null,
                new AfternoteCreateRequest.CredentialsRequest("id", "pw"),
                null,
                new AfternoteCreateRequest.PlaylistRequest(
                        null,
                        null,
                        List.of(new AfternoteCreateRequest.SongRequest("곡", "가수", null)),
                        null
                ),
                null
        );

        assertThatThrownBy(() -> social.validateCreate(request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> {
                    CustomException ce = (CustomException) ex;
                    assertThat(ce.getErrorCode()).isEqualTo(ErrorCode.INVALID_FIELD_FOR_SOCIAL);
                    assertThat(ce.getErrorCode().getMessage())
                            .contains("playlist")
                            .doesNotContain("credentials만")
                            .doesNotContain("수정할 수 있습니다");
                });
    }

    @Test
    @DisplayName("SOCIAL 수정 - receivers 포함해도 성공")
    void social_UpdateWithReceivers_Ok() {
        AfternoteCreateRequest request = new AfternoteCreateRequest(
                AfternoteCategoryType.SOCIAL,
                "인스타그램",
                null,
                null,
                new AfternoteCreateRequest.CredentialsRequest("id", "pw"),
                List.of(new AfternoteCreateRequest.ReceiverRequest(1L)),
                null,
                null
        );

        assertThatCode(() -> social.validateUpdate(request)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SOCIAL 수정 - playlist 포함 시 1616, 생성/수정 문맥이 섞이지 않음")
    void social_UpdateWithPlaylist_RejectsWithoutCreateWording() {
        AfternoteCreateRequest request = new AfternoteCreateRequest(
                AfternoteCategoryType.SOCIAL,
                "인스타그램",
                null,
                null,
                null,
                null,
                new AfternoteCreateRequest.PlaylistRequest(
                        null,
                        null,
                        List.of(new AfternoteCreateRequest.SongRequest("곡", "가수", null)),
                        null
                ),
                null
        );

        assertThatThrownBy(() -> social.validateUpdate(request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> {
                    CustomException ce = (CustomException) ex;
                    assertThat(ce.getErrorCode()).isEqualTo(ErrorCode.INVALID_FIELD_FOR_SOCIAL);
                    assertThat(ce.getErrorCode().getMessage())
                            .contains("playlist")
                            .doesNotContain("credentials만")
                            .doesNotContain("수정할 수 있습니다");
                });
    }

    @Test
    @DisplayName("BUSINESS 생성 - receivers·actions 없어도 성공")
    void business_WithoutReceiversAndActions_Ok() {
        AfternoteCreateRequest request = new AfternoteCreateRequest(
                AfternoteCategoryType.BUSINESS,
                "네이버 메일",
                null,
                null,
                new AfternoteCreateRequest.CredentialsRequest("id", "pw"),
                null,
                null,
                null
        );

        assertThatCode(() -> business.validateCreate(request)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("BUSINESS 임시저장 - credentials 없어도 성공")
    void business_DraftWithoutCredentials_Ok() {
        AfternoteCreateRequest request = new AfternoteCreateRequest(
                AfternoteCategoryType.BUSINESS,
                "네이버 메일",
                null,
                null,
                null,
                null,
                null,
                true
        );

        assertThatCode(() -> business.validateCreate(request)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("BUSINESS 생성 - playlist 포함 시 1616, playlist 필드 기준 메시지")
    void business_CreateWithPlaylist_RejectsWithFieldMessage() {
        AfternoteCreateRequest request = new AfternoteCreateRequest(
                AfternoteCategoryType.BUSINESS,
                "네이버 메일",
                null,
                null,
                new AfternoteCreateRequest.CredentialsRequest("id", "pw"),
                null,
                new AfternoteCreateRequest.PlaylistRequest(
                        null,
                        null,
                        List.of(new AfternoteCreateRequest.SongRequest("곡", "가수", null)),
                        null
                ),
                null
        );

        assertThatThrownBy(() -> business.validateCreate(request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> {
                    CustomException ce = (CustomException) ex;
                    assertThat(ce.getErrorCode()).isEqualTo(ErrorCode.INVALID_FIELD_FOR_SOCIAL);
                    assertThat(ce.getErrorCode().getMessage())
                            .contains("playlist")
                            .doesNotContain("credentials만")
                            .doesNotContain("수정할 수 있습니다");
                });
    }

    @Test
    @DisplayName("BUSINESS 수정 - receivers 포함해도 성공")
    void business_UpdateWithReceivers_Ok() {
        AfternoteCreateRequest request = new AfternoteCreateRequest(
                AfternoteCategoryType.BUSINESS,
                "네이버 메일",
                null,
                null,
                new AfternoteCreateRequest.CredentialsRequest("id", "pw"),
                List.of(new AfternoteCreateRequest.ReceiverRequest(1L)),
                null,
                null
        );

        assertThatCode(() -> business.validateUpdate(request)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("BUSINESS 수정 - playlist 포함 시 1616, 생성/수정 문맥이 섞이지 않음")
    void business_UpdateWithPlaylist_RejectsWithoutCreateWording() {
        AfternoteCreateRequest request = new AfternoteCreateRequest(
                AfternoteCategoryType.BUSINESS,
                "네이버 메일",
                null,
                null,
                null,
                null,
                new AfternoteCreateRequest.PlaylistRequest(
                        null,
                        null,
                        List.of(new AfternoteCreateRequest.SongRequest("곡", "가수", null)),
                        null
                ),
                null
        );

        assertThatThrownBy(() -> business.validateUpdate(request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> {
                    CustomException ce = (CustomException) ex;
                    assertThat(ce.getErrorCode()).isEqualTo(ErrorCode.INVALID_FIELD_FOR_SOCIAL);
                    assertThat(ce.getErrorCode().getMessage())
                            .contains("playlist")
                            .doesNotContain("credentials만")
                            .doesNotContain("수정할 수 있습니다");
                });
    }

    @Test
    @DisplayName("GALLERY 생성 - receivers·actions 없어도 성공")
    void gallery_WithoutReceiversAndActions_Ok() {
        AfternoteCreateRequest request = new AfternoteCreateRequest(
                AfternoteCategoryType.GALLERY,
                "갤러리",
                null,
                null,
                null,
                List.of(),
                null,
                null
        );

        assertThatCode(() -> gallery.validateCreate(request)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("PLAYLIST 생성 - receivers 없어도 성공")
    void playlist_WithoutReceivers_Ok() {
        AfternoteCreateRequest request = new AfternoteCreateRequest(
                AfternoteCategoryType.PLAYLIST,
                "추억 노트",
                null,
                null,
                null,
                null,
                new AfternoteCreateRequest.PlaylistRequest(
                        null,
                        null,
                        List.of(new AfternoteCreateRequest.SongRequest("곡", "가수", null)),
                        null
                ),
                null
        );

        assertThatCode(() -> playlist.validateCreate(request)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("PLAYLIST 임시저장 - playlist 없어도 성공")
    void playlist_DraftWithoutPlaylist_Ok() {
        AfternoteCreateRequest request = new AfternoteCreateRequest(
                AfternoteCategoryType.PLAYLIST,
                "추억 노트",
                null,
                null,
                null,
                null,
                null,
                true
        );

        assertThatCode(() -> playlist.validateCreate(request)).doesNotThrowAnyException();
    }
}
