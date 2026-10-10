package com.collabnotes.dto;

import com.collabnotes.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public class WorkspaceDtos {

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CreateWorkspaceRequest {
        @NotBlank
        private String name;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @lombok.Builder
    public static class WorkspaceResponse {
        private Long id;
        private String name;
        private Long ownerId;
        private String ownerName;
        private Role myRole;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class InviteMemberRequest {
        @NotBlank @Email
        private String email;

        @NotBlank
        private String role; // EDITOR or VIEWER
    }
}
