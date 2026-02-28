package com.example.youtubesummarizer.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SummaryResponse {
    private boolean success;
    private String summary;
    private String errorMessage;

    public static SummaryResponse success(String summary) {
        return new SummaryResponse(true, summary, null);
    }

    public static SummaryResponse error(String errorMessage) {
        return new SummaryResponse(false, null, errorMessage);
    }
}
