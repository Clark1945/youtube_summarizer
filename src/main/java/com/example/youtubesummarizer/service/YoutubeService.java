package com.example.youtubesummarizer.service;

import com.example.youtubesummarizer.exception.VideoNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class YoutubeService {

    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile(
            "(?:youtube\\.com/watch\\?v=|youtu\\.be/|youtube\\.com/embed/)([a-zA-Z0-9_-]{11})"
    );

    public String extractVideoId(String url) {
        Matcher matcher = VIDEO_ID_PATTERN.matcher(url);
        if (matcher.find()) return matcher.group(1);
        if (url.matches("[a-zA-Z0-9_-]{11}")) return url;
        throw new VideoNotFoundException("無效的 YouTube 網址，請確認後再試");
    }
}
