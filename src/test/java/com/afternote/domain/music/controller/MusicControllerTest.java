package com.afternote.domain.music.controller;

import com.afternote.domain.music.dto.GetMusicSearchResponse;
import com.afternote.domain.music.dto.MusicSearchItemDto;
import com.afternote.domain.music.service.ItunesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MusicControllerTest {

    @InjectMocks
    private MusicController musicController;

    @Mock
    private ItunesService itunesService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(musicController).build();
    }

    @Test
    @DisplayName("노래 검색 API 성공")
    void searchMusic_Success() throws Exception {
        GetMusicSearchResponse response = new GetMusicSearchResponse(List.of(
            new MusicSearchItemDto("artist", "song", "album")
        ));
        given(itunesService.searchMusic("iu")).willReturn(response);

        mockMvc.perform(get("/music/search").param("keyword", "iu"))
                .andExpect(status().isOk())
            .andExpect(jsonPath("$.tracks[0].title").value("song"))
            .andExpect(jsonPath("$.tracks[0].artist").value("artist"));

        verify(itunesService).searchMusic("iu");
    }

    @Test
    @DisplayName("노래 검색 API 실패 - keyword 누락")
    void searchMusic_MissingKeyword_Fail() throws Exception {
        mockMvc.perform(get("/music/search"))
                .andExpect(status().isBadRequest());
    }
}
