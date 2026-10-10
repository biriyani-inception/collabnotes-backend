package com.collabnotes.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

public class PageDtos {

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CreatePageRequest {
        @NotBlank
        private String title;
        private Long parentId;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class BlockRequest {
        private Long id;
        private String type;
        private String content;
        private Integer position;
        private Boolean completed;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class UpdatePageRequest {
        private String title;
        private String content;
        private List<BlockRequest> blocks;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class BlockResponse {
        private Long id;
        private String type;
        private String content;
        private Integer position;
        private Boolean completed;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PageResponse {
        private Long id;
        private String title;
        private String content;
        private Long workspaceId;
        private Long parentId;
        private String createdByName;
        private Instant createdAt;
        private Instant updatedAt;
        private List<BlockResponse> blocks;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PageSummary {
        private Long id;
        private String title;
        private Long parentId;
    }
}
