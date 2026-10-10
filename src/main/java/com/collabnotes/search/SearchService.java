package com.collabnotes.search;

import com.collabnotes.dto.SearchDtos.SearchHit;
import com.collabnotes.dto.WorkspaceDtos.WorkspaceResponse;
import com.collabnotes.model.*;
import com.collabnotes.repository.*;
import com.collabnotes.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Ponytail: naively CASE-INSENSITIVE LIKE on title + block.content + page.content.
// Per-page %-wildcard matches are fine up to ~10k pages. Switch to PostgreSQL pg_trgm/GIN
// when this gets slow.
@Service
@RequiredArgsConstructor
public class SearchService {

    private final PageRepository pageRepository;
    private final BlockRepository blockRepository;
    private final WorkspaceService workspaceService;
    private final WorkspaceMemberRepository memberRepository;

    @Transactional(readOnly = true)
    public List<SearchHit> searchInWorkspace(Long workspaceId, Long userId, String rawQuery, int limit) {
        workspaceService.requireMembership(workspaceService.getWorkspaceOrThrow(workspaceId), userId);
        String q = sanitize(rawQuery);
        if (q.isEmpty()) return List.of();
        return collectHits(pageRepository.findByWorkspaceId(workspaceId), q, Math.max(1, Math.min(limit, 50)));
    }

    @Transactional(readOnly = true)
    public List<SearchHit> searchAllForUser(Long userId, String rawQuery, int limit) {
        String q = sanitize(rawQuery);
        if (q.isEmpty()) return List.of();
        int cap = Math.max(1, Math.min(limit, 50));
        List<SearchHit> hits = new ArrayList<>();
        for (WorkspaceResponse w : workspaceService.listMyWorkspaces(userId)) {
            if (hits.size() >= cap) break;
            Workspace ws = workspaceService.getWorkspaceOrThrow(w.getId());
            hits.addAll(collectHits(pageRepository.findByWorkspaceId(ws.getId()), q, cap - hits.size()));
        }
        return hits;
    }

    private List<SearchHit> collectHits(List<Page> pages, String q, int cap) {
        List<SearchHit> hits = new ArrayList<>();
        for (Page p : pages) {
            if (hits.size() >= cap) break;
            String title = p.getTitle() == null ? "" : p.getTitle();
            String titleLc = title.toLowerCase(Locale.ROOT);
            boolean titleMatch = titleLc.contains(q);
            String snippet = null;

            if (!titleMatch) {
                // Ponytail: only iterate the (already position-ordered) blocks if title didn't match.
                for (Block b : blockRepository.findByPageIdOrderByPositionAsc(p.getId())) {
                    String content = b.getContent() == null ? "" : b.getContent();
                    if (content.toLowerCase(Locale.ROOT).contains(q)) {
                        snippet = excerpt(content, q);
                        break;
                    }
                }
                if (snippet == null && p.getContent() != null
                        && p.getContent().toLowerCase(Locale.ROOT).contains(q)) {
                    snippet = excerpt(p.getContent(), q);
                }
                if (snippet == null) continue;
            }

            hits.add(SearchHit.builder()
                    .pageId(p.getId())
                    .title(title.isBlank() ? "Untitled" : title)
                    .workspaceId(p.getWorkspace().getId())
                    .workspaceName(p.getWorkspace().getName())
                    .parentId(p.getParent() != null ? p.getParent().getId() : null)
                    .snippet(snippet)
                    .build());
        }
        return hits;
    }

    private static String sanitize(String raw) {
        if (raw == null) return "";
        // Strip SQL wildcard meta so users can't pull the whole table with "%%".
        String q = raw.trim().toLowerCase(Locale.ROOT).replace('%', ' ').replace('_', ' ');
        // Squash whitespace between tokens — Postgres LIKE is exact but H2 ignores patterns
        // unless we wrap with %, which we do at the call sites.
        return q;
    }

    private static String excerpt(String content, String q) {
        String lower = content.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf(q);
        if (idx < 0) return content.length() > 80 ? content.substring(0, 80) + "…" : content;
        int from = Math.max(0, idx - 30);
        int to = Math.min(content.length(), idx + q.length() + 50);
        String s = content.substring(from, to).replaceAll("\\s+", " ");
        return (from > 0 ? "…" : "") + s + (to < content.length() ? "…" : "");
    }
}
