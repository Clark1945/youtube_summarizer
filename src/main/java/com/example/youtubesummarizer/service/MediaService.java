package com.example.youtubesummarizer.service;

import com.example.youtubesummarizer.config.CacheConfig;
import com.example.youtubesummarizer.exception.NoSubtitlesException;
import com.example.youtubesummarizer.exception.ServiceUnavailableException;
import com.example.youtubesummarizer.exception.VideoNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
public class MediaService {

    private final RestTemplate restTemplate;

    /**
     * Self-injection（@Lazy）讓 getSummary 內部呼叫 getTranscript 時
     * 也能走 Spring AOP proxy，確保 @Cacheable 正常運作。
     */
    @Autowired
    @Lazy
    private MediaService self;

    @Value("${app.media-service.url:http://localhost:5001}")
    private String mediaServiceUrl;

    public MediaService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // ── 對外入口 ──────────────────────────────────────────────────────────────

    /**
     * 完整流程：取字幕 → 生成摘要。
     * 結果以 "videoId:language" 為 key 快取 1hr。
     */
    @Cacheable(value = CacheConfig.SUMMARIES, key = "#videoId + ':' + #language")
    public String getSummary(String videoId, String language) {
        String transcript = self.getTranscript(videoId);   // 透過 proxy → transcript cache
        return callSummarize(transcript, language);
    }

    // ── Step 1: GET /transcript（有 cache）────────────────────────────────────

    /**
     * 抓取字幕並快取，相同 videoId 1hr 內不重複呼叫 Python。
     */
    @Cacheable(value = CacheConfig.TRANSCRIPTS, key = "#videoId")
    public String getTranscript(String videoId) {
        String url = mediaServiceUrl + "/transcript?videoId=" + videoId;
        log.info("Calling Python /transcript videoId={}", videoId);

        try {
            ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
            JsonNode body = response.getBody();
            if (body == null) throw new ServiceUnavailableException("Python /transcript 回應為空");

            String transcript = body.path("transcript").asText("");
            if (transcript.isBlank()) throw new NoSubtitlesException("字幕內容為空");

            log.info("Transcript received, length={}", transcript.length());
            return transcript;

        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            log.warn("Python /transcript 回應 HTTP {} videoId={}", status, videoId);
            if (status == 404) throw new VideoNotFoundException("找不到該影片");
            if (status == 422) throw new NoSubtitlesException("該影片沒有可用的字幕，不支援快速總結");
            throw new ServiceUnavailableException("字幕服務暫時無法使用");

        } catch (HttpServerErrorException e) {
            log.warn("Python /transcript 回應 HTTP {} videoId={}", e.getStatusCode().value(), videoId);
            throw new ServiceUnavailableException("字幕服務暫時無法使用");

        } catch (ResourceAccessException e) {
            throw new ServiceUnavailableException("無法連接 media-service，請確認 Python 服務已啟動（port 5001）");
        }
    }

    // ── Step 2: POST /summarize（無 cache，由 getSummary 層統一 cache）────────

    private String callSummarize(String transcript, String language) {
        String url = mediaServiceUrl + "/summarize";
        log.info("Calling Python /summarize language={}, transcript_length={}", language, transcript.length());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of("transcript", transcript, "language", language);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<JsonNode> response =
                    restTemplate.exchange(url, HttpMethod.POST, entity, JsonNode.class);

            JsonNode responseBody = response.getBody();
            if (responseBody == null) throw new ServiceUnavailableException("Python /summarize 回應為空");

            String summary = responseBody.path("summary").asText("");
            if (summary.isBlank()) throw new ServiceUnavailableException("Python /summarize 回應缺少 summary 欄位");

            log.info("Summary received, length={}", summary.length());
            return summary;

        } catch (HttpClientErrorException e) {
            log.warn("Python /summarize 回應 HTTP {}", e.getStatusCode().value());
            throw new ServiceUnavailableException("摘要服務暫時無法使用");

        } catch (HttpServerErrorException e) {
            log.warn("Python /summarize 回應 HTTP {}", e.getStatusCode().value());
            throw new ServiceUnavailableException("摘要服務暫時無法使用");

        } catch (ResourceAccessException e) {
            throw new ServiceUnavailableException("無法連接 media-service，請確認 Python 服務已啟動（port 5001）");
        }
    }
}
