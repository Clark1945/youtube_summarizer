package com.example.youtubesummarizer.service;

import com.example.youtubesummarizer.exception.NoSubtitlesException;
import com.example.youtubesummarizer.exception.ServiceUnavailableException;
import com.example.youtubesummarizer.exception.VideoNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeService {

    private final RestTemplate restTemplate;

    @Value("${app.transcript-service.url:http://localhost:5001}")
    private String transcriptServiceUrl;

    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile(
            "(?:youtube\\.com/watch\\?v=|youtu\\.be/|youtube\\.com/embed/)([a-zA-Z0-9_-]{11})"
    );

    public String extractVideoId(String url) {
        Matcher matcher = VIDEO_ID_PATTERN.matcher(url);
        if (matcher.find()) return matcher.group(1);
        if (url.matches("[a-zA-Z0-9_-]{11}")) return url;
        throw new VideoNotFoundException("無效的 YouTube 網址，請確認後再試");
    }

    @Cacheable("transcripts")
    public String getTranscript(String videoId) {
        ServiceUnavailableException lastException = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return fetchTranscriptFromService(videoId);
            } catch (ServiceUnavailableException e) {
                lastException = e;
                log.warn("字幕服務呼叫失敗，第 {} 次嘗試，videoId={}", attempt + 1, videoId);
            }
        }
        throw lastException;
    }

    private String fetchTranscriptFromService(String videoId) {
        String url = transcriptServiceUrl + "/transcript?videoId=" + videoId;
        log.debug("呼叫字幕服務 url={}", url);

        try {
            ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
            JsonNode body = response.getBody();
            if (body == null) throw new ServiceUnavailableException("字幕服務回應為空");

            String transcript = body.path("transcript").asText("");
            if (transcript.isEmpty()) throw new NoSubtitlesException("字幕內容為空");

            log.info("字幕取得成功 videoId={}, length={}", videoId, transcript.length());
            return transcript;

        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            log.warn("字幕服務回應 HTTP {} videoId={}", status, videoId);
            if (status == 404) throw new VideoNotFoundException("找不到該影片");
            if (status == 422) throw new NoSubtitlesException("該影片沒有可用的字幕，不支援快速總結");
            throw new ServiceUnavailableException("字幕服務不可用");

        } catch (ResourceAccessException e) {
            throw new ServiceUnavailableException("無法連接字幕服務，請確認 Python 服務已啟動");
        }
    }
}
