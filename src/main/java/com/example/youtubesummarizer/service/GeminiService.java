package com.example.youtubesummarizer.service;

import com.example.youtubesummarizer.exception.ServiceUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.gemini.api-key}")
    private String apiKey;

    @Value("${app.gemini.model:gemini-1.5-flash}")
    private String model;

    private static final int MAX_TRANSCRIPT_LENGTH = 30_000;

    private static final Map<String, String> LANGUAGE_MAP = Map.ofEntries(
            Map.entry("zh-tw", "Traditional Chinese (繁體中文)"),
            Map.entry("zh-hk", "Traditional Chinese (繁體中文)"),
            Map.entry("zh-cn", "Simplified Chinese (简体中文)"),
            Map.entry("zh", "Chinese (中文)"),
            Map.entry("en", "English"),
            Map.entry("en-us", "English"),
            Map.entry("en-gb", "English"),
            Map.entry("ja", "Japanese (日本語)"),
            Map.entry("ko", "Korean (한국어)"),
            Map.entry("es", "Spanish (Español)"),
            Map.entry("fr", "French (Français)"),
            Map.entry("de", "German (Deutsch)"),
            Map.entry("pt", "Portuguese (Português)"),
            Map.entry("it", "Italian (Italiano)")
    );

    public String getLanguageName(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return "English";
        }
        String primary = acceptLanguage.split("[,;]")[0].trim().toLowerCase();
        if (LANGUAGE_MAP.containsKey(primary)) {
            return LANGUAGE_MAP.get(primary);
        }
        String prefix = primary.contains("-") ? primary.substring(0, primary.indexOf("-")) : primary;
        return LANGUAGE_MAP.getOrDefault(prefix, "English");
    }

    @Cacheable(value = "summaries", key = "#videoId + ':' + #language")
    public String getSummary(String videoId, String transcript, String language) {
        ServiceUnavailableException lastException = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return callGeminiApi(transcript, language);
            } catch (ServiceUnavailableException e) {
                lastException = e;
                log.warn("Gemini API 呼叫失敗，第 {} 次嘗試，videoId={}", attempt + 1, videoId);
            }
        }
        throw lastException;
    }

    private String callGeminiApi(String transcript, String language) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent?key=" + apiKey;

        String truncatedTranscript = transcript.length() > MAX_TRANSCRIPT_LENGTH
                ? transcript.substring(0, MAX_TRANSCRIPT_LENGTH) + "..."
                : transcript;

        String prompt = String.format(
                "Please summarize the following YouTube video transcript in %s. "
                + "Format the summary as bullet points using • as the bullet character. "
                + "Be concise but comprehensive, capturing the main points and key insights. "
                + "Reply with only the bullet-point summary, no introduction or closing remarks.\n\n"
                + "Transcript:\n%s",
                language, truncatedTranscript
        );

        ObjectNode requestBody = objectMapper.createObjectNode();
        ArrayNode contents = requestBody.putArray("contents");
        ArrayNode parts = contents.addObject().putArray("parts");
        parts.addObject().put("text", prompt);

        ObjectNode generationConfig = requestBody.putObject("generationConfig");
        generationConfig.put("temperature", 0.3);
        generationConfig.put("maxOutputTokens", 2048);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity;
        try {
            entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);
        } catch (Exception e) {
            throw new ServiceUnavailableException("內部序列化錯誤");
        }

        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        } catch (ResourceAccessException e) {
            throw new ServiceUnavailableException("AI 服務逾時");
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Gemini API HTTP {} 錯誤，body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ServiceUnavailableException("AI 服務暫時無法使用");
        } catch (Exception e) {
            log.error("Gemini API 呼叫失敗: {}", e.getMessage(), e);
            throw new ServiceUnavailableException("AI 服務暫時無法使用");
        }

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("candidates")
                    .get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text")
                    .asText();
        } catch (Exception e) {
            log.error("解析 Gemini 回應失敗，body={}", response.getBody());
            throw new ServiceUnavailableException("AI 服務回應格式異常");
        }
    }
}
