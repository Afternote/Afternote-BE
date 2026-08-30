package com.afternote.domain.afternote.service.validation;

import com.afternote.domain.afternote.dto.AfternoteCreateRequest;
import com.afternote.domain.afternote.model.AfternoteCategoryType;
import com.afternote.domain.image.service.S3Service;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlaylistValidationStrategy implements AfternoteCategoryValidationStrategy {

    private static final String AFTERNOTES_DIRECTORY = "afternotes";

    private final S3Service s3Service;

    @Override
    public AfternoteCategoryType category() {
        return AfternoteCategoryType.PLAYLIST;
    }

    @Override
    public void validateCreate(AfternoteCreateRequest request) {
        if (request.getCredentials() != null) {
            throw new CustomException(ErrorCode.INVALID_FIELD_FOR_PLAYLIST);
        }
        if (request.getActions() != null) {
            throw new CustomException(ErrorCode.INVALID_FIELD_FOR_PLAYLIST);
        }

        if (request.getPlaylist() != null) {
            validatePlaylistMedia(request.getPlaylist(), true);
        }

        // draft면 playlist/songs 필수 검증 완화
        if (!request.isDraftValue()) {
            requirePlaylistSongs(request);
        } else if (request.getPlaylist() != null && request.getPlaylist().getSongs() != null) {
            validateSongFields(request.getPlaylist().getSongs());
        }

        // receivers 는 선택 (없으면 빈 상태로 생성)
        AfternoteValidationCommons.validateOptionalReceivers(request);
    }

    @Override
    public void validateUpdate(AfternoteCreateRequest request) {
        if (request.getCredentials() != null) {
            throw new CustomException(ErrorCode.INVALID_FIELD_FOR_PLAYLIST);
        }
        if (request.getActions() != null) {
            throw new CustomException(ErrorCode.INVALID_FIELD_FOR_PLAYLIST);
        }

        if (request.getPlaylist() != null) {
            validatePlaylistMedia(request.getPlaylist(), false);
            if (request.getPlaylist().getSongs() != null) {
                validateSongFields(request.getPlaylist().getSongs());
            }
        }

        AfternoteValidationCommons.validateOptionalReceivers(request);
    }

    private void requirePlaylistSongs(AfternoteCreateRequest request) {
        if (request.getPlaylist() == null) {
            throw new CustomException(ErrorCode.PLAYLIST_REQUIRED);
        }
        if (request.getPlaylist().getSongs() == null || request.getPlaylist().getSongs().isEmpty()) {
            throw new CustomException(ErrorCode.PLAYLIST_SONGS_REQUIRED);
        }
        validateSongFields(request.getPlaylist().getSongs());
    }

    private void validatePlaylistMedia(AfternoteCreateRequest.PlaylistRequest playlist, boolean create) {
        if (shouldValidateSlot(create, playlist.memorialPhotoUrlSpecified(), playlist.getMemorialPhotoUrl())) {
            requireNonBlank(playlist.getMemorialPhotoUrl(), ErrorCode.MEMORIAL_PHOTO_URL_CANNOT_BE_EMPTY);
            requireManaged(playlist.getMemorialPhotoUrl(), S3Service.MediaKind.IMAGE);
        }
        if (shouldValidateSlot(create, playlist.memorialAudioUrlSpecified(), playlist.getMemorialAudioUrl())) {
            requireNonBlank(playlist.getMemorialAudioUrl(), ErrorCode.MEMORIAL_AUDIO_URL_CANNOT_BE_EMPTY);
            requireManaged(playlist.getMemorialAudioUrl(), S3Service.MediaKind.AUDIO);
        }
        if (shouldValidateSlot(create, playlist.memorialVideoSpecified(), playlist.getMemorialVideo())) {
            AfternoteCreateRequest.MemorialVideoRequest video = playlist.getMemorialVideo();
            if (video.getVideoUrl() == null || video.getVideoUrl().isBlank()) {
                throw new CustomException(ErrorCode.VIDEO_URL_CANNOT_BE_EMPTY);
            }
            requireManaged(video.getVideoUrl(), S3Service.MediaKind.VIDEO);
            if (video.getThumbnailUrl() != null) {
                if (video.getThumbnailUrl().isBlank()) {
                    throw new CustomException(ErrorCode.THUMBNAIL_URL_CANNOT_BE_EMPTY);
                }
                requireManaged(video.getThumbnailUrl(), S3Service.MediaKind.IMAGE);
            }
        }
        if (playlist.getSongs() != null) {
            for (AfternoteCreateRequest.SongRequest song : playlist.getSongs()) {
                if (song == null || song.getCoverUrl() == null) {
                    continue;
                }
                if (song.getCoverUrl().isBlank()) {
                    throw new CustomException(ErrorCode.FIELD_CANNOT_BE_EMPTY);
                }
                requireManaged(song.getCoverUrl(), S3Service.MediaKind.IMAGE);
            }
        }
    }

    private boolean shouldValidateSlot(boolean create, boolean specified, Object value) {
        if (create) {
            return value != null;
        }
        return specified && value != null;
    }

    private void requireNonBlank(String value, ErrorCode errorCode) {
        if (value.isBlank()) {
            throw new CustomException(errorCode);
        }
    }

    private void requireManaged(String rawUrlOrKey, S3Service.MediaKind kind) {
        if (!s3Service.isManagedObjectKeyInDirectory(rawUrlOrKey, AFTERNOTES_DIRECTORY)) {
            throw new CustomException(ErrorCode.UNMANAGED_MEDIA_URL);
        }
        if (!s3Service.isManagedMediaInDirectory(rawUrlOrKey, AFTERNOTES_DIRECTORY, kind)) {
            throw new CustomException(ErrorCode.INVALID_FILE_EXTENSION);
        }
    }

    private void validateSongFields(java.util.List<AfternoteCreateRequest.SongRequest> songs) {
        for (AfternoteCreateRequest.SongRequest song : songs) {
            if (song == null) {
                throw new CustomException(ErrorCode.PLAYLIST_SONG_INVALID);
            }
            if (song.getTitle() == null || song.getTitle().isBlank()) {
                throw new CustomException(ErrorCode.PLAYLIST_SONG_TITLE_REQUIRED);
            }
            if (song.getArtist() == null || song.getArtist().isBlank()) {
                throw new CustomException(ErrorCode.PLAYLIST_SONG_ARTIST_REQUIRED);
            }
        }
    }
}
