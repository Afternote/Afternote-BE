package com.afternote.domain.afternote.service.validation;

import com.afternote.domain.afternote.dto.AfternoteCreateRequest;
import com.afternote.domain.afternote.model.AfternoteCategoryType;
import com.afternote.domain.image.service.S3Service;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PlaylistValidationStrategyMediaTest {

    @InjectMocks
    private PlaylistValidationStrategy strategy;

    @Mock
    private S3Service s3Service;

    @Test
    @DisplayName("생성 시 javascript 스킴 영정사진은 1805")
    void create_UnmanagedPhotoUrl_Fails() {
        given(s3Service.isManagedObjectKeyInDirectory("javascript:alert(1)", "afternotes")).willReturn(false);

        AfternoteCreateRequest request = new AfternoteCreateRequest(
                AfternoteCategoryType.PLAYLIST,
                "추억",
                null,
                null,
                null,
                null,
                new AfternoteCreateRequest.PlaylistRequest(
                        null, "javascript:alert(1)", null, null, null),
                true
        );

        assertThatThrownBy(() -> strategy.validateCreate(request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.UNMANAGED_MEDIA_URL));
    }

    @Test
    @DisplayName("생성 시 음성 슬롯에 이미지 키면 1801")
    void create_WrongAudioExtension_Fails() {
        String key = "afternotes/staging/1/a.jpg";
        given(s3Service.isManagedObjectKeyInDirectory(key, "afternotes")).willReturn(true);
        given(s3Service.isManagedMediaInDirectory(key, "afternotes", S3Service.MediaKind.AUDIO)).willReturn(false);

        AfternoteCreateRequest request = new AfternoteCreateRequest(
                AfternoteCategoryType.PLAYLIST,
                "추억",
                null,
                null,
                null,
                null,
                new AfternoteCreateRequest.PlaylistRequest(null, null, null, null, key),
                true
        );

        assertThatThrownBy(() -> strategy.validateCreate(request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_FILE_EXTENSION));
    }
}
