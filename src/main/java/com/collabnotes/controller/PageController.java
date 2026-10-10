package com.collabnotes.controller;

import com.collabnotes.dto.PageDtos.*;
import com.collabnotes.dto.PageVersionDtos.*;
import com.collabnotes.security.CurrentUser;
import com.collabnotes.service.PageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PageController {

    private final PageService pageService;
    private final CurrentUser currentUser;

    @PostMapping("/api/workspaces/{workspaceId}/pages")
    public ResponseEntity<PageResponse> create(@PathVariable Long workspaceId,
                                                @Valid @RequestBody CreatePageRequest req,
                                                Authentication auth) {
        return ResponseEntity.ok(pageService.createPage(workspaceId, currentUser.idOf(auth), req));
    }

    @GetMapping("/api/workspaces/{workspaceId}/pages")
    public ResponseEntity<List<PageSummary>> list(@PathVariable Long workspaceId, Authentication auth) {
        return ResponseEntity.ok(pageService.listPages(workspaceId, currentUser.idOf(auth)));
    }

    @GetMapping("/api/pages/{pageId}")
    public ResponseEntity<PageResponse> get(@PathVariable Long pageId, Authentication auth) {
        return ResponseEntity.ok(pageService.getPage(pageId, currentUser.idOf(auth)));
    }

    @PutMapping("/api/pages/{pageId}")
    public ResponseEntity<PageResponse> update(@PathVariable Long pageId,
                                                @RequestBody UpdatePageRequest req,
                                                Authentication auth) {
        return ResponseEntity.ok(pageService.updatePage(pageId, currentUser.idOf(auth), req));
    }

    @DeleteMapping("/api/pages/{pageId}")
    public ResponseEntity<Void> delete(@PathVariable Long pageId, Authentication auth) {
        pageService.deletePage(pageId, currentUser.idOf(auth));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/pages/{pageId}/versions")
    public ResponseEntity<List<PageVersionSummary>> versions(@PathVariable Long pageId, Authentication auth) {
        return ResponseEntity.ok(pageService.listVersions(pageId, currentUser.idOf(auth)));
    }

    @GetMapping("/api/pages/{pageId}/versions/{n}")
    public ResponseEntity<PageVersionResponse> version(@PathVariable Long pageId,
                                                        @PathVariable Integer n,
                                                        Authentication auth) {
        return ResponseEntity.ok(pageService.getVersion(pageId, n, currentUser.idOf(auth)));
    }

    @PostMapping("/api/pages/{pageId}/versions/{n}/restore")
    public ResponseEntity<PageResponse> restore(@PathVariable Long pageId,
                                                  @PathVariable Integer n,
                                                  Authentication auth) {
        return ResponseEntity.ok(pageService.restoreVersion(pageId, n, currentUser.idOf(auth)));
    }
}
