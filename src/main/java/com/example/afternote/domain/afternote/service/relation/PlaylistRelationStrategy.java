package com.example.afternote.domain.afternote.service.relation;

import com.example.afternote.domain.afternote.dto.AfternoteCreateRequest;
import com.example.afternote.domain.afternote.model.Afternote;
import com.example.afternote.domain.afternote.model.AfternoteCategoryType;
import com.example.afternote.domain.afternote.model.AfternotePlaylist;
import com.example.afternote.domain.afternote.model.AfternotePlaylistItem;
import com.example.afternote.domain.afternote.repository.AfternotePlaylistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlaylistRelationStrategy implements AfternoteCategoryRelationStrategy {

    private static final String DEFAULT_PLAYLIST_TITLE = "추모 플레이리스트";

    private final AfternotePlaylistRepository playlistRepository;

    @Override
    public AfternoteCategoryType category() {
        return AfternoteCategoryType.PLAYLIST;
    }

    @Override
    public void save(Afternote afternote, AfternoteCreateRequest request) {
        if (request.getPlaylist() == null) return;

        AfternotePlaylist.MemorialVideo memorialVideo = createMemorialVideo(request.getPlaylist().getMemorialVideo());
        AfternotePlaylist playlist = createPlaylist(
                afternote,
                request.getPlaylist().getAtmosphere(),
                request.getPlaylist().getMemorialPhotoUrl(),
                memorialVideo);

        playlist = playlistRepository.save(playlist);

        if (request.getPlaylist().getSongs() != null) {
            int sortOrder = 1;
            for (AfternoteCreateRequest.SongRequest songReq : request.getPlaylist().getSongs()) {
                playlist.getItems().add(createPlaylistItem(playlist, songReq, sortOrder++));
            }
            playlistRepository.save(playlist);
        }
    }

    @Override
    public void update(Afternote afternote, AfternoteCreateRequest request) {
        if (request.getPlaylist() == null) return;

        AfternotePlaylist playlist = afternote.getPlaylist();

        if (playlist == null) {
            AfternotePlaylist newPlaylist = createPlaylist(
                    afternote,
                    request.getPlaylist().getAtmosphere(),
                    request.getPlaylist().getMemorialPhotoUrl(),
                    request.getPlaylist().getMemorialVideo() != null
                            ? createMemorialVideo(request.getPlaylist().getMemorialVideo())
                            : null);

            newPlaylist = playlistRepository.save(newPlaylist);

            if (request.getPlaylist().getSongs() != null) {
                int sortOrder = 1;
                for (AfternoteCreateRequest.SongRequest songReq : request.getPlaylist().getSongs()) {
                    newPlaylist.getItems().add(createPlaylistItem(newPlaylist, songReq, sortOrder++));
                }
                playlistRepository.save(newPlaylist);
            }
            return;
        }

        AfternotePlaylist.MemorialVideo memorialVideo = request.getPlaylist().getMemorialVideo() != null
                ? createMemorialVideo(request.getPlaylist().getMemorialVideo())
                : null;
        playlist.update(
                request.getPlaylist().getAtmosphere(),
                request.getPlaylist().getMemorialPhotoUrl(),
                memorialVideo);

        if (request.getPlaylist().getSongs() != null) {
            playlist.getItems().clear();
            int sortOrder = 1;
            for (AfternoteCreateRequest.SongRequest songReq : request.getPlaylist().getSongs()) {
                playlist.getItems().add(createPlaylistItem(playlist, songReq, sortOrder++));
            }
        }

        playlistRepository.save(playlist);
    }

    private AfternotePlaylist.MemorialVideo createMemorialVideo(AfternoteCreateRequest.MemorialVideoRequest request) {
        if (request == null) return null;

        return AfternotePlaylist.MemorialVideo.builder()
                .videoUrl(request.getVideoUrl())
                .thumbnailUrl(request.getThumbnailUrl())
                .build();
    }

    private AfternotePlaylist createPlaylist(
            Afternote afternote,
            String atmosphere,
            String memorialPhotoUrl,
            AfternotePlaylist.MemorialVideo memorialVideo
    ) {
        return AfternotePlaylist.builder()
                .afternote(afternote)
                .title(atmosphere != null ? atmosphere : DEFAULT_PLAYLIST_TITLE)
                .atmosphere(atmosphere)
                .memorialPhotoUrl(memorialPhotoUrl)
                .memorialVideo(memorialVideo)
                .build();
    }

    private AfternotePlaylistItem createPlaylistItem(
            AfternotePlaylist playlist,
            AfternoteCreateRequest.SongRequest song,
            int sortOrder
    ) {
        return AfternotePlaylistItem.builder()
                .playlist(playlist)
                .songTitle(song.getTitle())
                .artist(song.getArtist())
                .coverUrl(song.getCoverUrl())
                .sortOrder(sortOrder)
                .build();
    }
}
