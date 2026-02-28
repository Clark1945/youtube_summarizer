package com.example.youtubesummarizer.service;

import com.example.youtubesummarizer.exception.NoSubtitlesException;
import com.example.youtubesummarizer.exception.ServiceUnavailableException;
import com.example.youtubesummarizer.exception.VideoNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile(
            "(?:youtube\\.com/watch\\?v=|youtu\\.be/|youtube\\.com/embed/)([a-zA-Z0-9_-]{11})"
    );

    public String extractVideoId(String url) {
        Matcher matcher = VIDEO_ID_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        // Accept raw video ID
        if (url.matches("[a-zA-Z0-9_-]{11}")) {
            return url;
        }
        throw new VideoNotFoundException("無效的 YouTube 網址，請確認後再試");
    }

    @Cacheable("transcripts")
    public String getTranscript(String videoId) {
        ServiceUnavailableException lastException = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return fetchTranscript(videoId);
            } catch (ServiceUnavailableException e) {
                lastException = e;
                log.warn("字幕取得失敗，第 {} 次嘗試，videoId={}", attempt + 1, videoId);
            }
        }
        throw lastException;
    }

    private String fetchTranscript(String videoId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.set("Accept-Language", "en-US,en;q=0.9");
        headers.set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        String pageUrl = "https://www.youtube.com/watch?v=" + videoId + "&gl=US&hl=en";

        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(pageUrl, HttpMethod.GET, entity, String.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new VideoNotFoundException("找不到該影片");
            }
            throw new ServiceUnavailableException("無法連接到 YouTube");
        } catch (ResourceAccessException e) {
            throw new ServiceUnavailableException("連接 YouTube 逾時");
        }

        String html = response.getBody();
        if (html == null) {
            throw new VideoNotFoundException("找不到該影片");
        }
        if (html.contains("\"status\":\"ERROR\"") || html.contains("Video unavailable")) {
            throw new VideoNotFoundException("該影片不存在或無法存取");
        }

        String captionUrl = extractCaptionUrl(html);
        return fetchAndParseCaptions(captionUrl);
    }

    private String extractCaptionUrl(String html) {
        // Find ytInitialPlayerResponse JSON block in the page HTML
        String marker = "ytInitialPlayerResponse = ";
        int startIdx = html.indexOf(marker);
        if (startIdx == -1) {
            marker = "ytInitialPlayerResponse=";
            startIdx = html.indexOf(marker);
        }
        if (startIdx == -1) {
            throw new NoSubtitlesException("該影片不支援快速總結");
        }

        startIdx = html.indexOf("{", startIdx);
        if (startIdx == -1) {
            throw new NoSubtitlesException("該影片不支援快速總結");
        }

        // Balance brace counting to extract the full JSON object
        int depth = 0;
        int endIdx = startIdx;
        boolean inString = false;
        boolean escaped = false;

        for (int i = startIdx; i < html.length(); i++) {
            char c = html.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\' && inString) { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (!inString) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) { endIdx = i + 1; break; }
                }
            }
        }

        if (endIdx <= startIdx) {
            throw new NoSubtitlesException("該影片不支援快速總結");
        }

        try {
            JsonNode root = objectMapper.readTree(html.substring(startIdx, endIdx));
            JsonNode captionTracks = root.path("captions")
                    .path("playerCaptionsTracklistRenderer")
                    .path("captionTracks");

            if (captionTracks.isMissingNode() || !captionTracks.isArray() || captionTracks.isEmpty()) {
                throw new NoSubtitlesException("該影片沒有可用的字幕，不支援快速總結");
            }

            // Prefer English track; fallback to first available
            String firstUrl = null;
            String bestUrl = null;
            for (JsonNode track : captionTracks) {
                String langCode = track.path("languageCode").asText("");
                String baseUrl = track.path("baseUrl").asText("");
                if (baseUrl.isEmpty()) continue;
                if (firstUrl == null) firstUrl = baseUrl;
                if (langCode.startsWith("en")) { bestUrl = baseUrl; break; }
            }

            String captionUrl = (bestUrl != null) ? bestUrl : firstUrl;
            if (captionUrl == null) {
                throw new NoSubtitlesException("該影片沒有可用的字幕，不支援快速總結");
            }
            return captionUrl;

        } catch (NoSubtitlesException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析字幕資訊失敗", e);
            throw new NoSubtitlesException("該影片不支援快速總結");
        }
    }

    private String fetchAndParseCaptions(String captionUrl) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(captionUrl, HttpMethod.GET, entity, String.class);
        } catch (ResourceAccessException e) {
            throw new ServiceUnavailableException("連接字幕服務逾時");
        } catch (Exception e) {
            throw new ServiceUnavailableException("無法取得字幕");
        }

        //ERROR response=<200 OK OK,[Date:"Sat, 28 Feb 2026 04:29:13 GMT", Pragma:"no-cache", Expires:"Fri, 01 Jan 1990 00:00:00 GMT", Cache-Control:"no-cache, must-revalidate", X-Content-Type-Options:"nosniff", Content-Type:"text/html; charset=UTF-8", Server:"video-timedtext", Content-Length:"0", X-XSS-Protection:"0", X-Frame-Options:"SAMEORIGIN", Alt-Svc:"h3=":443"; ma=2592000,h3-29=":443"; ma=2592000"]>
        String xml = response.getBody();
        if (xml == null || xml.isBlank()) {
            throw new NoSubtitlesException("字幕內容為空");
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Disable external entities for security (XXE prevention)
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            NodeList textNodes = doc.getElementsByTagName("text");

            if (textNodes.getLength() == 0) {
                throw new NoSubtitlesException("字幕內容為空");
            }

            StringBuilder transcript = new StringBuilder();
            for (int i = 0; i < textNodes.getLength(); i++) {
                String text = textNodes.item(i).getTextContent().trim();
                if (!text.isEmpty()) {
                    transcript.append(text).append(" ");
                }
            }
            return transcript.toString().trim();

        } catch (NoSubtitlesException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析字幕 XML 失敗", e);
            throw new ServiceUnavailableException("字幕解析失敗");
        }
    }
}
