package com.collabnotes.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

public class PageVersionDtos {

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PageVersionSummary {
        private Integer versionNumber;
        private String title;
        private String authorName;
        private Instant createdAt;
        private Integer blockCount;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PageVersionResponse {
        private Integer versionNumber;
        private String title;
        private String content;
        private List<PageDtos.BlockResponse> blocks;
        private String authorName;
        private Instant createdAt;
    }
}
