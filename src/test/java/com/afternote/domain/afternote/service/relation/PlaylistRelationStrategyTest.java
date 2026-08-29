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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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
        return new AfternoteCreateRequest(
                AfternoteCategoryType.PLAYLIST,
                null,
                null,
                null,
                null,
                null,
                new AfternoteCreateRequest.PlaylistRequest(atmosphere, null, null, null),
                true
        );
    }
}
