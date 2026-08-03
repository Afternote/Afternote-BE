package com.afternote.domain.afternote.service.validation;

import com.afternote.domain.afternote.dto.AfternoteCreateRequest;
import com.afternote.domain.afternote.model.AfternoteCategoryType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;

class AfternoteCreateValidationStrategyTest {

    private final SocialValidationStrategy social = new SocialValidationStrategy();
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
                null
        );

        assertThatCode(() -> social.validateCreate(request)).doesNotThrowAnyException();
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
                )
        );

        assertThatCode(() -> playlist.validateCreate(request)).doesNotThrowAnyException();
    }
}
