package com.example.youtubesummarizer.controller;

import com.example.youtubesummarizer.exception.NoSubtitlesException;
import com.example.youtubesummarizer.exception.ServiceUnavailableException;
import com.example.youtubesummarizer.exception.VideoNotFoundException;
import com.example.youtubesummarizer.model.SummaryRequest;
import com.example.youtubesummarizer.model.SummaryResponse;
import com.example.youtubesummarizer.service.MediaService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RestController
@RequiredArgsConstructor
public class SummaryController {

    private final MediaService mediaService;

    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile("(?:youtube\\.com/watch\\?v=|youtu\\.be/|youtube\\.com/embed/)([a-zA-Z0-9_-]{11})");

    private static final Map<String, String> LANGUAGE_MAP = Map.ofEntries(
            Map.entry("zh-tw", "Traditional Chinese (繁體中文)"),
            Map.entry("zh-hk", "Traditional Chinese (繁體中文)"),
            Map.entry("zh-cn", "Simplified Chinese (简体中文)"),
            Map.entry("zh",    "Chinese (中文)"),
            Map.entry("en",    "English"),
            Map.entry("en-us", "English"),
            Map.entry("en-gb", "English"),
            Map.entry("ja",    "Japanese (日本語)"),
            Map.entry("ko",    "Korean (한국어)"),
            Map.entry("es",    "Spanish (Español)"),
            Map.entry("fr",    "French (Français)"),
            Map.entry("de",    "German (Deutsch)"),
            Map.entry("pt",    "Portuguese (Português)"),
            Map.entry("it",    "Italian (Italiano)")
    );

    @PostMapping("/api/summarize")
    public ResponseEntity<SummaryResponse> summarize(
            @RequestBody SummaryRequest request,
            HttpServletRequest httpRequest) {

        String url = request.getUrl();
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(SummaryResponse.error("請輸入 YouTube 網址"));
        }

        String language = resolveLanguage(httpRequest.getHeader("Accept-Language"));
        log.info("收到請求 url={}, language={}", url, language);

        try {
            String videoId = extractVideoId(url);
            String summary = mediaService.getSummary(videoId, language);
            return ResponseEntity.ok(SummaryResponse.success(summary));

        } catch (VideoNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(SummaryResponse.error(e.getMessage()));
        } catch (NoSubtitlesException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(SummaryResponse.error(e.getMessage()));
        } catch (ServiceUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(SummaryResponse.error("伺服器忙碌中，請稍後再試"));
        } catch (Exception e) {
            log.error("未預期的錯誤", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(SummaryResponse.error("發生未知錯誤，請稍後再試"));
        }
    }

    private static String extractVideoId(String url) {
        Matcher matcher = VIDEO_ID_PATTERN.matcher(url);
        if (matcher.find()) return matcher.group(1);
        if (url.matches("[a-zA-Z0-9_-]{11}")) return url;
        throw new VideoNotFoundException("無效的 YouTube 網址，請確認後再試");
    }

    private static String resolveLanguage(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) return "English";
        String primary = acceptLanguage.split("[,;]")[0].trim().toLowerCase();
        if (LANGUAGE_MAP.containsKey(primary)) return LANGUAGE_MAP.get(primary);
        String prefix = primary.contains("-") ? primary.substring(0, primary.indexOf("-")) : primary;
        return LANGUAGE_MAP.getOrDefault(prefix, "English");
    }
}
