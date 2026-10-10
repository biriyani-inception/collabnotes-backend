package com.collabnotes.dto;

import com.collabnotes.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class MemberDtos {

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class MemberResponse {
        private Long userId;
        private String name;
        private String email;
        private Role role;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class MemberListResponse {
        private List<MemberResponse> members;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class UpdateMemberRoleRequest {
        private String role;  // EDITOR or VIEWER (OWNER changes are rejected — see service)
    }
}
