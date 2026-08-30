package com.afternote.domain.afternote.service.relation;

import com.afternote.domain.afternote.dto.AfternoteCreateRequest;
import com.afternote.domain.afternote.model.Afternote;
import com.afternote.domain.afternote.model.AfternoteCategoryType;
import com.afternote.domain.afternote.model.AfternotePlaylist;
import com.afternote.domain.afternote.model.AfternotePlaylistItem;
import com.afternote.domain.afternote.repository.AfternotePlaylistRepository;
import com.afternote.domain.image.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlaylistRelationStrategy implements AfternoteCategoryRelationStrategy {

    private static final String DEFAULT_PLAYLIST_TITLE = "추모 플레이리스트";
    private static final String AFTERNOTES_DIRECTORY = "afternotes";

    private final AfternotePlaylistRepository playlistRepository;
    private final S3Service s3Service;

    @Override
    public AfternoteCategoryType category() {
        return AfternoteCategoryType.PLAYLIST;
    }

    @Override
    public void save(Afternote afternote, AfternoteCreateRequest request) {
        if (request.getPlaylist() == null) return;

        Long userId = afternote.getUser().getId();
        AfternoteCreateRequest.PlaylistRequest playlistRequest = request.getPlaylist();
        AfternotePlaylist playlist = createPlaylist(
                afternote,
                playlistRequest.getAtmosphere(),
                createMediaSlot(userId, playlistRequest.getMemorialPhotoUrl(), S3Service.MediaKind.IMAGE),
                createMemorialVideo(userId, playlistRequest.getMemorialVideo()),
                createMediaSlot(userId, playlistRequest.getMemorialAudioUrl(), S3Service.MediaKind.AUDIO));

        playlist = playlistRepository.save(playlist);

        if (playlistRequest.getSongs() != null) {
            int sortOrder = 1;
            for (AfternoteCreateRequest.SongRequest songReq : playlistRequest.getSongs()) {
                playlist.getItems().add(createPlaylistItem(userId, playlist, songReq, sortOrder++));
            }
            playlistRepository.save(playlist);
        }
    }

    @Override
    public void update(Afternote afternote, AfternoteCreateRequest request) {
        if (request.getPlaylist() == null) return;

        Long userId = afternote.getUser().getId();
        AfternotePlaylist playlist = afternote.getPlaylist();
        AfternoteCreateRequest.PlaylistRequest playlistRequest = request.getPlaylist();

        if (playlist == null) {
            AfternotePlaylist newPlaylist = createPlaylist(
                    afternote,
                    playlistRequest.getAtmosphere(),
                    createMediaSlot(userId, playlistRequest.getMemorialPhotoUrl(), S3Service.MediaKind.IMAGE),
                    createMemorialVideo(userId, playlistRequest.getMemorialVideo()),
                    createMediaSlot(userId, playlistRequest.getMemorialAudioUrl(), S3Service.MediaKind.AUDIO));

            newPlaylist = playlistRepository.save(newPlaylist);

            if (playlistRequest.getSongs() != null) {
                int sortOrder = 1;
                for (AfternoteCreateRequest.SongRequest songReq : playlistRequest.getSongs()) {
                    newPlaylist.getItems().add(createPlaylistItem(userId, newPlaylist, songReq, sortOrder++));
                }
                playlistRepository.save(newPlaylist);
            }
            return;
        }

        playlist.update(
                playlistRequest.getAtmosphere(),
                applyMediaSlot(
                        userId,
                        playlist.getMemorialPhotoUrl(),
                        playlistRequest.getMemorialPhotoUrl(),
                        playlistRequest.memorialPhotoUrlSpecified(),
                        S3Service.MediaKind.IMAGE),
                playlistRequest.memorialPhotoUrlSpecified(),
                applyMemorialVideo(
                        userId,
                        playlist.getMemorialVideo(),
                        playlistRequest.getMemorialVideo(),
                        playlistRequest.memorialVideoSpecified()),
                playlistRequest.memorialVideoSpecified(),
                applyMediaSlot(
                        userId,
                        playlist.getMemorialAudioUrl(),
                        playlistRequest.getMemorialAudioUrl(),
                        playlistRequest.memorialAudioUrlSpecified(),
                        S3Service.MediaKind.AUDIO),
                playlistRequest.memorialAudioUrlSpecified());

        if (playlistRequest.getSongs() != null) {
            playlist.getItems().clear();
            int sortOrder = 1;
            for (AfternoteCreateRequest.SongRequest songReq : playlistRequest.getSongs()) {
                playlist.getItems().add(createPlaylistItem(userId, playlist, songReq, sortOrder++));
            }
        }

        playlistRepository.save(playlist);
    }

    private AfternotePlaylist.MemorialVideo createMemorialVideo(
            Long userId,
            AfternoteCreateRequest.MemorialVideoRequest request
    ) {
        if (request == null) return null;

        return AfternotePlaylist.MemorialVideo.builder()
                .videoUrl(createMediaSlot(userId, request.getVideoUrl(), S3Service.MediaKind.VIDEO))
                .thumbnailUrl(createMediaSlot(userId, request.getThumbnailUrl(), S3Service.MediaKind.IMAGE))
                .build();
    }

    private AfternotePlaylist.MemorialVideo applyMemorialVideo(
            Long userId,
            AfternotePlaylist.MemorialVideo current,
            AfternoteCreateRequest.MemorialVideoRequest requested,
            boolean specified
    ) {
        if (!specified) {
            return current;
        }
        if (requested == null) {
            if (current != null) {
                s3Service.deleteManagedObject(current.getVideoUrl(), AFTERNOTES_DIRECTORY);
                s3Service.deleteManagedObject(current.getThumbnailUrl(), AFTERNOTES_DIRECTORY);
            }
            return null;
        }

        String nextVideo = createMediaSlot(userId, requested.getVideoUrl(), S3Service.MediaKind.VIDEO);
        String nextThumbnail = createMediaSlot(userId, requested.getThumbnailUrl(), S3Service.MediaKind.IMAGE);
        if (current != null) {
            replaceIfChanged(current.getVideoUrl(), nextVideo);
            replaceIfChanged(current.getThumbnailUrl(), nextThumbnail);
        }
        return AfternotePlaylist.MemorialVideo.builder()
                .videoUrl(nextVideo)
                .thumbnailUrl(nextThumbnail)
                .build();
    }

    private AfternotePlaylist createPlaylist(
            Afternote afternote,
            String atmosphere,
            String memorialPhotoUrl,
            AfternotePlaylist.MemorialVideo memorialVideo,
            String memorialAudioUrl
    ) {
        return AfternotePlaylist.builder()
                .afternote(afternote)
                .title(resolvePlaylistTitle(afternote))
                .atmosphere(atmosphere)
                .memorialPhotoUrl(memorialPhotoUrl)
                .memorialVideo(memorialVideo)
                .memorialAudioUrl(memorialAudioUrl)
                .build();
    }

    private String resolvePlaylistTitle(Afternote afternote) {
        String title = afternote.getTitle();
        if (title == null || title.isBlank()) {
            return DEFAULT_PLAYLIST_TITLE;
        }
        return title.length() > 100 ? title.substring(0, 100) : title;
    }

    private AfternotePlaylistItem createPlaylistItem(
            Long userId,
            AfternotePlaylist playlist,
            AfternoteCreateRequest.SongRequest song,
            int sortOrder
    ) {
        return AfternotePlaylistItem.builder()
                .playlist(playlist)
                .songTitle(song.getTitle())
                .artist(song.getArtist())
                .coverUrl(createMediaSlot(userId, song.getCoverUrl(), S3Service.MediaKind.IMAGE))
                .sortOrder(sortOrder)
                .build();
    }

    private String createMediaSlot(Long userId, String rawUrlOrKey, S3Service.MediaKind kind) {
        if (rawUrlOrKey == null || rawUrlOrKey.isBlank()) {
            return null;
        }
        return s3Service.promoteManagedMediaKey(AFTERNOTES_DIRECTORY, userId, rawUrlOrKey, kind);
    }

    private String applyMediaSlot(
            Long userId,
            String current,
            String requested,
            boolean specified,
            S3Service.MediaKind kind
    ) {
        if (!specified) {
            return current;
        }
        if (requested == null || requested.isBlank()) {
            s3Service.deleteManagedObject(current, AFTERNOTES_DIRECTORY);
            return null;
        }
        String promoted = s3Service.promoteManagedMediaKey(AFTERNOTES_DIRECTORY, userId, requested, kind);
        replaceIfChanged(current, promoted);
        return promoted;
    }

    private void replaceIfChanged(String current, String next) {
        if (current != null && (next == null || !s3Service.sameStorageKey(current, next))) {
            s3Service.deleteManagedObject(current, AFTERNOTES_DIRECTORY);
        }
    }
}
