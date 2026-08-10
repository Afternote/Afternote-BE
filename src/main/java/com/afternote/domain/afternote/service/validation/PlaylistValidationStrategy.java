package com.afternote.domain.afternote.service.validation;

import com.afternote.domain.afternote.dto.AfternoteCreateRequest;
import com.afternote.domain.afternote.model.AfternoteCategoryType;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class PlaylistValidationStrategy implements AfternoteCategoryValidationStrategy {

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

        if (request.getPlaylist() != null && request.getPlaylist().getSongs() != null) {
            validateSongFields(request.getPlaylist().getSongs());
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

    private void validateSongFields(java.util.List<AfternoteCreateRequest.SongRequest> songs) {
        for (AfternoteCreateRequest.SongRequest song : songs) {
            if (song.getTitle() == null || song.getTitle().isBlank()) {
                throw new CustomException(ErrorCode.PLAYLIST_SONG_TITLE_REQUIRED);
            }
            if (song.getArtist() == null || song.getArtist().isBlank()) {
                throw new CustomException(ErrorCode.PLAYLIST_SONG_ARTIST_REQUIRED);
            }
        }
    }
}
