package com.collabnotes.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class SearchDtos {

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SearchHit {
        private Long pageId;
        private String title;
        private Long workspaceId;
        private String workspaceName;
        private Long parentId;
        private String snippet;     // surrounding context for the match, or null if title-only
    }
}
