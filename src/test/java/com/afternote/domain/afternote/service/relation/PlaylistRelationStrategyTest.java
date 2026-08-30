package com.afternote.domain.afternote.service.relation;

import com.afternote.domain.afternote.dto.AfternoteCreateRequest;
import com.afternote.domain.afternote.model.Afternote;
import com.afternote.domain.afternote.model.AfternoteCategoryType;
import com.afternote.domain.afternote.model.AfternotePlaylist;
import com.afternote.domain.afternote.repository.AfternotePlaylistRepository;
import com.afternote.domain.image.service.S3Service;
import com.afternote.domain.user.model.AuthProvider;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.model.UserStatus;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PlaylistRelationStrategyTest {

    @InjectMocks
    private PlaylistRelationStrategy strategy;

    @Mock
    private AfternotePlaylistRepository playlistRepository;

    @Mock
    private S3Service s3Service;

    @Test
    @DisplayName("애프터노트 title이 있으면 playlist title로 쓰고, atmosphere는 title에 넣지 않는다")
    void save_UsesAfternoteTitle_NotAtmosphere() {
        Afternote afternote = playlistAfternote("추억");
        String atmosphere = "a".repeat(200);
        AfternoteCreateRequest request = playlistRequest(atmosphere);

        given(playlistRepository.save(any(AfternotePlaylist.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        strategy.save(afternote, request);

        ArgumentCaptor<AfternotePlaylist> captor = ArgumentCaptor.forClass(AfternotePlaylist.class);
        verify(playlistRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("추억");
        assertThat(captor.getValue().getAtmosphere()).isEqualTo(atmosphere);
    }

    @Test
    @DisplayName("애프터노트 title이 없으면 playlist title은 추모 플레이리스트")
    void save_MissingAfternoteTitle_UsesDefault() {
        Afternote afternote = playlistAfternote(null);
        AfternoteCreateRequest request = playlistRequest("차분한 분위기");

        given(playlistRepository.save(any(AfternotePlaylist.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        strategy.save(afternote, request);

        ArgumentCaptor<AfternotePlaylist> captor = ArgumentCaptor.forClass(AfternotePlaylist.class);
        verify(playlistRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("추모 플레이리스트");
        assertThat(captor.getValue().getAtmosphere()).isEqualTo("차분한 분위기");
    }

    @Test
    @DisplayName("생성 시 memorialAudioUrl 을 승격해 저장한다")
    void save_PromotesMemorialAudioUrl() {
        Afternote afternote = playlistAfternote("추억");
        String staging = "afternotes/staging/1/voice.m4a";
        String permanent = "afternotes/permanent/1/voice.m4a";
        given(s3Service.promoteManagedMediaKey("afternotes", 1L, staging, S3Service.MediaKind.AUDIO))
                .willReturn(permanent);
        given(playlistRepository.save(any(AfternotePlaylist.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        strategy.save(afternote, playlistRequest("분위기", null, null, staging));

        ArgumentCaptor<AfternotePlaylist> captor = ArgumentCaptor.forClass(AfternotePlaylist.class);
        verify(playlistRepository).save(captor.capture());
        assertThat(captor.getValue().getMemorialAudioUrl()).isEqualTo(permanent);
    }

    @Test
    @DisplayName("PATCH에서 memorialPhotoUrl JSON null 이면 S3와 DB에서 삭제한다")
    void update_NullMemorialPhoto_DeletesS3AndClears() {
        Afternote afternote = playlistAfternote("추억");
        String oldPhoto = "afternotes/permanent/1/old.jpg";
        AfternotePlaylist existing = AfternotePlaylist.builder()
                .afternote(afternote)
                .title("추억")
                .memorialPhotoUrl(oldPhoto)
                .build();
        ReflectionTestUtils.setField(afternote, "playlist", existing);
        given(playlistRepository.save(any(AfternotePlaylist.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        AfternoteCreateRequest request = new AfternoteCreateRequest(
                AfternoteCategoryType.PLAYLIST,
                null,
                null,
                null,
                null,
                null,
                AfternoteCreateRequest.PlaylistRequest.parsed(
                        null, null, null, null, null, true, false, false),
                true
        );

        strategy.update(afternote, request);

        verify(s3Service).deleteManagedObject(oldPhoto, "afternotes");
        assertThat(existing.getMemorialPhotoUrl()).isNull();
    }

    @Test
    @DisplayName("PATCH에서 memorialPhotoUrl 생략 시 기존 사진을 유지한다")
    void update_OmittedMemorialPhoto_KeepsExisting() {
        Afternote afternote = playlistAfternote("추억");
        String oldPhoto = "afternotes/permanent/1/old.jpg";
        AfternotePlaylist existing = AfternotePlaylist.builder()
                .afternote(afternote)
                .title("추억")
                .memorialPhotoUrl(oldPhoto)
                .build();
        ReflectionTestUtils.setField(afternote, "playlist", existing);
        given(playlistRepository.save(any(AfternotePlaylist.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        strategy.update(afternote, playlistRequest("새 분위기"));

        verify(s3Service, never()).deleteManagedObject(any(), any());
        assertThat(existing.getMemorialPhotoUrl()).isEqualTo(oldPhoto);
        assertThat(existing.getAtmosphere()).isEqualTo("새 분위기");
    }

    @Test
    @DisplayName("관리되지 않는 미디어 URL 은 저장하지 않는다")
    void save_UnmanagedPhotoUrl_Fails() {
        Afternote afternote = playlistAfternote("추억");
        given(s3Service.promoteManagedMediaKey(
                eq("afternotes"), eq(1L), eq("javascript:alert(1)"), eq(S3Service.MediaKind.IMAGE)))
                .willThrow(new CustomException(ErrorCode.UNMANAGED_MEDIA_URL));

        AfternoteCreateRequest request = playlistRequest("분위기", "javascript:alert(1)", null, null);

        assertThatThrownBy(() -> strategy.save(afternote, request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.UNMANAGED_MEDIA_URL));
        verify(playlistRepository, never()).save(any());
    }

    private static Afternote playlistAfternote(String title) {
        User user = User.builder()
                .email("u@test.com")
                .password("pw")
                .name("tester")
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return Afternote.builder()
                .user(user)
                .categoryType(AfternoteCategoryType.PLAYLIST)
                .title(title)
                .isDraft(true)
                .sortOrder(1)
                .build();
    }

    private static AfternoteCreateRequest playlistRequest(String atmosphere) {
        return playlistRequest(atmosphere, null, null, null);
    }

    private static AfternoteCreateRequest playlistRequest(
            String atmosphere,
            String memorialPhotoUrl,
            AfternoteCreateRequest.MemorialVideoRequest memorialVideo,
            String memorialAudioUrl
    ) {
        return new AfternoteCreateRequest(
                AfternoteCategoryType.PLAYLIST,
                null,
                null,
                null,
                null,
                null,
                new AfternoteCreateRequest.PlaylistRequest(
                        atmosphere, memorialPhotoUrl, null, memorialVideo, memorialAudioUrl),
                true
        );
    }
}
