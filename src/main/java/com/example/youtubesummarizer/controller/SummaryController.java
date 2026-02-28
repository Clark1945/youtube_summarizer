package com.example.youtubesummarizer.controller;

import com.example.youtubesummarizer.exception.NoSubtitlesException;
import com.example.youtubesummarizer.exception.ServiceUnavailableException;
import com.example.youtubesummarizer.exception.VideoNotFoundException;
import com.example.youtubesummarizer.model.SummaryRequest;
import com.example.youtubesummarizer.model.SummaryResponse;
import com.example.youtubesummarizer.service.MediaService;
import com.example.youtubesummarizer.service.YoutubeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Controller
@RequiredArgsConstructor
public class SummaryController {

    private final YoutubeService youtubeService;
    private final MediaService mediaService;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/api/summarize")
    @ResponseBody
    public ResponseEntity<SummaryResponse> summarize(
            @RequestBody SummaryRequest request,
            HttpServletRequest httpRequest) {

        String url = request.getUrl();
        if (url == null || url.isBlank()) {
            return ResponseEntity
                    .badRequest()
                    .body(SummaryResponse.error("請輸入 YouTube 網址"));
        }

        String acceptLanguage = httpRequest.getHeader("Accept-Language");
        String language = mediaService.getLanguageName(acceptLanguage);

        log.info("收到請求 url={}, language={}", url, language);

        try {
            String videoId = youtubeService.extractVideoId(url);
            String summary = mediaService.getSummary(videoId, language);
            return ResponseEntity.ok(SummaryResponse.success(summary));

        } catch (VideoNotFoundException e) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(SummaryResponse.error(e.getMessage()));

        } catch (NoSubtitlesException e) {
            return ResponseEntity
                    .status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(SummaryResponse.error(e.getMessage()));

        } catch (ServiceUnavailableException e) {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(SummaryResponse.error("伺服器忙碌中，請稍後再試"));

        } catch (Exception e) {
            log.error("未預期的錯誤", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(SummaryResponse.error("發生未知錯誤，請稍後再試"));
        }
    }
}
