package com.collabnotes.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class CollabDtos {
    public enum EventType { CONTENT_UPDATE, BLOCKS_UPDATE, CURSOR_MOVE, PRESENCE_JOIN, PRESENCE_LEAVE, TYPING }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class EditMessage {
        private EventType type;
        private String content;
        private Integer cursorPos;
        private List<com.collabnotes.dto.PageDtos.BlockRequest> blocks;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class BroadcastMessage {
        private EventType type;
        private Long pageId;
        private Long userId;
        private String userName;
        private String content;
        private Integer cursorPos;
        private List<com.collabnotes.dto.PageDtos.BlockRequest> blocks;
        private long timestamp;
    }
}
