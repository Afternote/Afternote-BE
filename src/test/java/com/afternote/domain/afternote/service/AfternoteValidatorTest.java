package com.afternote.domain.afternote.service;

import com.afternote.domain.afternote.dto.AfternoteCreateRequest;
import com.afternote.domain.afternote.dto.AfternoteUpdateRequest;
import com.afternote.domain.afternote.model.Afternote;
import com.afternote.domain.afternote.model.AfternoteCategoryType;
import com.afternote.domain.afternote.service.validation.AfternoteValidationStrategyFactory;
import com.afternote.domain.afternote.service.validation.BusinessValidationStrategy;
import com.afternote.domain.afternote.service.validation.GalleryValidationStrategy;
import com.afternote.domain.afternote.service.validation.PlaylistValidationStrategy;
import com.afternote.domain.afternote.service.validation.SocialValidationStrategy;
import com.afternote.domain.user.model.User;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AfternoteValidatorTest {

    private AfternoteValidator validator;

    @BeforeEach
    void setUp() {
        AfternoteValidationStrategyFactory factory = new AfternoteValidationStrategyFactory(List.of(
                new SocialValidationStrategy(),
                new BusinessValidationStrategy(),
                new GalleryValidationStrategy(),
                new PlaylistValidationStrategy()
        ));
        ReflectionTestUtils.invokeMethod(factory, "init");
        validator = new AfternoteValidator(factory);
    }

    @Test
    @DisplayName("생성 임시저장 PLAYLIST - playlist 없어도 성공")
    void create_DraftPlaylist_WithoutPlaylist_Ok() {
        AfternoteCreateRequest request = new AfternoteCreateRequest(
                AfternoteCategoryType.PLAYLIST,
                "추억",
                null,
                null,
                null,
                null,
                null,
                true
        );

        assertThatCode(() -> validator.validateCreateRequest(request)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("생성 정식 등록 PLAYLIST - playlist 없으면 400")
    void create_PublishedPlaylist_WithoutPlaylist_Fails() {
        AfternoteCreateRequest request = new AfternoteCreateRequest(
                AfternoteCategoryType.PLAYLIST,
                "추억",
                null,
                null,
                null,
                null,
                null,
                false
        );

        assertThatThrownBy(() -> validator.validateCreateRequest(request))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PLAYLIST_REQUIRED);
    }

    @Test
    @DisplayName("생성 정식 등록 SOCIAL - credentials 없으면 400")
    void create_PublishedSocial_WithoutCredentials_Fails() {
        AfternoteCreateRequest request = new AfternoteCreateRequest(
                AfternoteCategoryType.SOCIAL,
                "인스타",
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> validator.validateCreateRequest(request))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SOCIAL_CREDENTIALS_REQUIRED);
    }

    @Test
    @DisplayName("수정 - category 생략 성공")
    void update_OmitCategory_Ok() {
        AfternoteUpdateRequest request = new AfternoteUpdateRequest(
                null, "제목만", null, null, null, null, null, true
        );

        assertThatCode(() -> validator.validateUpdateRequest(request, AfternoteCategoryType.GALLERY))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("수정 - 동일 category 호환 전송 성공")
    void update_SameCategory_Ok() {
        AfternoteUpdateRequest request = new AfternoteUpdateRequest(
                AfternoteCategoryType.SOCIAL, null, null, null, null, null, null, true
        );

        assertThatCode(() -> validator.validateUpdateRequest(request, AfternoteCategoryType.SOCIAL))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("수정 - 다른 category면 1614")
    void update_DifferentCategory_Fails() {
        AfternoteUpdateRequest request = new AfternoteUpdateRequest(
                AfternoteCategoryType.PLAYLIST, null, null, null, null, null, null, true
        );

        assertThatThrownBy(() -> validator.validateUpdateRequest(request, AfternoteCategoryType.SOCIAL))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CATEGORY_CANNOT_BE_CHANGED);
    }

    @Test
    @DisplayName("수정 임시저장 유지 PLAYLIST - playlist 없어도 발행 검증 통과")
    void update_StayDraftPlaylist_WithoutPlaylist_Ok() {
        Afternote afternote = draftPlaylist();
        AfternoteCreateRequest write = new AfternoteCreateRequest(
                AfternoteCategoryType.PLAYLIST, null, null, null, null, null, null, true
        );

        assertThatCode(() -> validator.validatePublishRequirements(write, afternote))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("수정 draft→발행 PLAYLIST - playlist·곡 없으면 400")
    void update_PublishPlaylist_WithoutPlaylist_Fails() {
        Afternote afternote = draftPlaylist();
        AfternoteCreateRequest write = new AfternoteCreateRequest(
                AfternoteCategoryType.PLAYLIST, null, null, null, null, null, null, false
        );

        assertThatThrownBy(() -> validator.validatePublishRequirements(write, afternote))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PLAYLIST_REQUIRED);
    }

    @Test
    @DisplayName("수정 draft→발행 PLAYLIST - 요청에 곡 있으면 성공")
    void update_PublishPlaylist_WithSongsInRequest_Ok() {
        Afternote afternote = draftPlaylist();
        AfternoteCreateRequest write = new AfternoteCreateRequest(
                AfternoteCategoryType.PLAYLIST,
                null,
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
                false
        );

        assertThatCode(() -> validator.validatePublishRequirements(write, afternote))
                .doesNotThrowAnyException();
    }

    private static Afternote draftPlaylist() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 1L);
        Afternote afternote = Afternote.builder()
                .user(user)
                .categoryType(AfternoteCategoryType.PLAYLIST)
                .title("추억")
                .isDraft(true)
                .sortOrder(1)
                .build();
        ReflectionTestUtils.setField(afternote, "id", 10L);
        return afternote;
    }
}
