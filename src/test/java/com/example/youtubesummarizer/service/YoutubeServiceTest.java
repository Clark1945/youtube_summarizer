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

    /** 原始測試影片（自動生成字幕，zh-Hant） */
    private static final String TARGET_VIDEO_ID = "c7RzVm1wj_A";

    /** 對照組：Rick Astley - Never Gonna Give You Up（高知名度，有英文字幕） */
    private static final String CONTROL_VIDEO_ID = "dQw4w9WgXcQ";

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

    // ── getTranscript integration tests (real HTTP) ─────────────────────────

    @Test
    @DisplayName("[對照組] 知名英文影片應能透過 fallback chain 取得字幕")
    void getTranscript_controlVideo_returnsTranscript() {
        String transcript = youtubeService.getTranscript(CONTROL_VIDEO_ID);

        assertThat(transcript).isNotBlank();
        assertThat(transcript.length()).isGreaterThan(50);

        System.out.println("=== 對照組字幕取得成功 ===");
        System.out.println("總長度：" + transcript.length() + " 字元");
        System.out.println("前 200 字：" + transcript.substring(0, Math.min(200, transcript.length())));
    }

    @Test
    @DisplayName("[目標影片] 自動生成字幕影片應能透過 fallback chain 取得字幕")
    void getTranscript_targetVideo_returnsTranscript() {
        String transcript = youtubeService.getTranscript(TARGET_VIDEO_ID);

        assertThat(transcript).isNotBlank();
        assertThat(transcript.length()).isGreaterThan(50);

        System.out.println("=== 目標影片字幕取得成功 ===");
        System.out.println("總長度：" + transcript.length() + " 字元");
        System.out.println("前 200 字：" + transcript.substring(0, Math.min(200, transcript.length())));
    }
}
