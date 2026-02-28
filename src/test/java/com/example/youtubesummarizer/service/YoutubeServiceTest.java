package com.example.youtubesummarizer.service;

import com.example.youtubesummarizer.exception.VideoNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class YoutubeServiceTest {

    @Autowired
    private YoutubeService youtubeService;

    private static final String TARGET_VIDEO_ID = "c7RzVm1wj_A";

    // ── extractVideoId (unit, no network) ───────────────────────────────────

    @Test
    @DisplayName("extractVideoId - watch URL")
    void extractVideoId_watchUrl() {
        assertThat(youtubeService.extractVideoId(
                "https://www.youtube.com/watch?v=" + TARGET_VIDEO_ID))
                .isEqualTo(TARGET_VIDEO_ID);
    }

    @Test
    @DisplayName("extractVideoId - 短網址 youtu.be")
    void extractVideoId_shortUrl() {
        assertThat(youtubeService.extractVideoId("https://youtu.be/" + TARGET_VIDEO_ID))
                .isEqualTo(TARGET_VIDEO_ID);
    }

    @Test
    @DisplayName("extractVideoId - embed URL")
    void extractVideoId_embedUrl() {
        assertThat(youtubeService.extractVideoId(
                "https://www.youtube.com/embed/" + TARGET_VIDEO_ID))
                .isEqualTo(TARGET_VIDEO_ID);
    }

    @Test
    @DisplayName("extractVideoId - 直接給 videoId")
    void extractVideoId_rawId() {
        assertThat(youtubeService.extractVideoId(TARGET_VIDEO_ID))
                .isEqualTo(TARGET_VIDEO_ID);
    }

    @Test
    @DisplayName("extractVideoId - 無效 URL 應拋出 VideoNotFoundException")
    void extractVideoId_invalidUrl_throws() {
        assertThatThrownBy(() -> youtubeService.extractVideoId("https://www.google.com"))
                .isInstanceOf(VideoNotFoundException.class);
    }
}
