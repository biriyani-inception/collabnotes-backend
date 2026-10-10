package com.collabnotes.controller;

import com.collabnotes.dto.MemberDtos.*;
import com.collabnotes.dto.SearchDtos.SearchHit;
import com.collabnotes.dto.WorkspaceDtos.*;
import com.collabnotes.search.SearchService;
import com.collabnotes.security.CurrentUser;
import com.collabnotes.service.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final SearchService searchService;
    private final CurrentUser currentUser;

    @PostMapping
    public ResponseEntity<WorkspaceResponse> create(@Valid @RequestBody CreateWorkspaceRequest req,
                                                      Authentication auth) {
        return ResponseEntity.ok(workspaceService.createWorkspace(currentUser.idOf(auth), req));
    }

    @GetMapping
    public ResponseEntity<List<WorkspaceResponse>> mine(Authentication auth) {
        return ResponseEntity.ok(workspaceService.listMyWorkspaces(currentUser.idOf(auth)));
    }

    @PostMapping("/{workspaceId}/invite")
    public ResponseEntity<Void> invite(@PathVariable Long workspaceId,
                                        @Valid @RequestBody InviteMemberRequest req,
                                        Authentication auth) {
        workspaceService.inviteMember(workspaceId, currentUser.idOf(auth), req);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{workspaceId}/members")
    public ResponseEntity<List<MemberResponse>> members(@PathVariable Long workspaceId, Authentication auth) {
        return ResponseEntity.ok(workspaceService.listMembers(workspaceId, currentUser.idOf(auth)));
    }

    @PatchMapping("/{workspaceId}/members/{userId}")
    public ResponseEntity<Void> updateRole(@PathVariable Long workspaceId,
                                            @PathVariable Long userId,
                                            @RequestBody UpdateMemberRoleRequest req,
                                            Authentication auth) {
        workspaceService.updateMemberRole(workspaceId, currentUser.idOf(auth), userId, req.getRole());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{workspaceId}/members/{userId}")
    public ResponseEntity<Void> remove(@PathVariable Long workspaceId,
                                        @PathVariable Long userId,
                                        Authentication auth) {
        workspaceService.removeMember(workspaceId, currentUser.idOf(auth), userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{workspaceId}/search")
    public ResponseEntity<List<SearchHit>> searchInWorkspace(@PathVariable Long workspaceId,
                                                              @RequestParam("q") String q,
                                                              @RequestParam(value = "limit", defaultValue = "20") int limit,
                                                              Authentication auth) {
        return ResponseEntity.ok(searchService.searchInWorkspace(workspaceId, currentUser.idOf(auth), q, limit));
    }

    @GetMapping("/search")
    public ResponseEntity<List<SearchHit>> searchAll(@RequestParam("q") String q,
                                                      @RequestParam(value = "limit", defaultValue = "20") int limit,
                                                      Authentication auth) {
        return ResponseEntity.ok(searchService.searchAllForUser(currentUser.idOf(auth), q, limit));
    }
}
