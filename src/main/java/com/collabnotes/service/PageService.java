package com.collabnotes.service;

import com.collabnotes.dto.PageDtos.*;
import com.collabnotes.dto.PageVersionDtos.*;
import com.collabnotes.model.*;
import com.collabnotes.repository.BlockRepository;
import com.collabnotes.repository.PageRepository;
import com.collabnotes.repository.PageVersionRepository;
import com.collabnotes.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PageService {

    private final PageRepository pageRepository;
    private final BlockRepository blockRepository;
    private final PageVersionRepository versionRepository;
    private final UserRepository userRepository;
    private final WorkspaceService workspaceService;

    @Transactional
    public PageResponse createPage(Long workspaceId, Long userId, CreatePageRequest req) {
        Workspace workspace = workspaceService.getWorkspaceOrThrow(workspaceId);
        workspaceService.requireRole(workspace, userId, Role.EDITOR);
        User creator = userRepository.getReferenceById(userId);

        Page parent = null;
        if (req.getParentId() != null) {
            parent = pageRepository.findById(req.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent page not found"));
        }

        Page page = Page.builder()
                .title(req.getTitle())
                .content("")
                .workspace(workspace)
                .parent(parent)
                .createdBy(creator)
                .build();

        page = pageRepository.save(page);
        Block first = Block.builder().page(page).type("paragraph").content("").position(0).completed(false).build();
        blockRepository.save(first);

        snapshot(page, creator, "created");
        return toResponse(pageRepository.findById(page.getId()).orElseThrow());
    }

    public List<PageSummary> listPages(Long workspaceId, Long userId) {
        Workspace workspace = workspaceService.getWorkspaceOrThrow(workspaceId);
        workspaceService.requireMembership(workspace, userId);
        return pageRepository.findByWorkspaceId(workspaceId).stream()
                .map(p -> PageSummary.builder().id(p.getId()).title(p.getTitle())
                        .parentId(p.getParent() != null ? p.getParent().getId() : null).build())
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse getPage(Long pageId, Long userId) {
        Page page = getPageOrThrow(pageId);
        workspaceService.requireMembership(page.getWorkspace(), userId);
        return toResponse(page);
    }

    @Transactional
    public PageResponse updatePage(Long pageId, Long userId, UpdatePageRequest req) {
        Page page = getPageOrThrow(pageId);
        workspaceService.requireRole(page.getWorkspace(), userId, Role.EDITOR);
        User actor = userRepository.getReferenceById(userId);

        // Snapshot BEFORE applying edits so versions are recoverable.
        snapshot(page, actor, "before edit");

        if (req.getTitle() != null) page.setTitle(req.getTitle());
        if (req.getBlocks() != null) replaceBlocks(page, req.getBlocks());
        if (req.getContent() != null) page.setContent(req.getContent());
        page.setUpdatedAt(Instant.now());

        Page saved = pageRepository.save(page);
        snapshot(saved, actor, "after edit");
        return toResponse(saved);
    }

    @Transactional
    public void deletePage(Long pageId, Long userId) {
        Page page = getPageOrThrow(pageId);
        workspaceService.requireRole(page.getWorkspace(), userId, Role.EDITOR);
        pageRepository.delete(page);
    }

    // -------------- Version history --------------

    @Transactional(readOnly = true)
    public List<PageVersionSummary> listVersions(Long pageId, Long userId) {
        Page page = getPageOrThrow(pageId);
        workspaceService.requireMembership(page.getWorkspace(), userId);
        return versionRepository.findByPageOrderByVersionNumberDesc(page).stream()
                .map(v -> PageVersionSummary.builder()
                        .versionNumber(v.getVersionNumber())
                        .title(v.getTitle())
                        .authorName(v.getAuthor() == null ? null : v.getAuthor().getName())
                        .createdAt(v.getCreatedAt())
                        .blockCount(blocksFromSnapshot(v.getBlocksSnapshot()).size())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public PageVersionResponse getVersion(Long pageId, Integer versionNumber, Long userId) {
        Page page = getPageOrThrow(pageId);
        workspaceService.requireMembership(page.getWorkspace(), userId);
        PageVersion v = versionRepository.findByPageAndVersionNumber(page, versionNumber)
                .orElseThrow(() -> new IllegalArgumentException("Version not found"));
        return PageVersionResponse.builder()
                .versionNumber(v.getVersionNumber())
                .title(v.getTitle())
                .content(v.getContent())
                .blocks(blocksFromSnapshot(v.getBlocksSnapshot()).stream().map(b ->
                        BlockResponse.builder().id(null).type(b.type).content(b.content)
                                .position(b.position).completed(b.completed).build()
                ).toList())
                .authorName(v.getAuthor() == null ? null : v.getAuthor().getName())
                .createdAt(v.getCreatedAt())
                .build();
    }

    /** Restore creates a new top version with the chosen version's contents (non-destructive). */
    @Transactional
    public PageResponse restoreVersion(Long pageId, Integer versionNumber, Long userId) {
        Page page = getPageOrThrow(pageId);
        workspaceService.requireRole(page.getWorkspace(), userId, Role.EDITOR);
        User actor = userRepository.getReferenceById(userId);
        PageVersion target = versionRepository.findByPageAndVersionNumber(page, versionNumber)
                .orElseThrow(() -> new IllegalArgumentException("Version not found"));

        // Snapshot the live state before clobbering it.
        snapshot(page, actor, "before restore to v" + versionNumber);

        page.setTitle(target.getTitle());
        page.setContent(target.getContent());
        List<SimpleBlock> blocks = blocksFromSnapshot(target.getBlocksSnapshot());

        blockRepository.deleteByPageId(page.getId());
        List<Block> rebuilt = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            SimpleBlock sb = blocks.get(i);
            rebuilt.add(Block.builder().page(page).type(sb.type).content(sb.content)
                    .position(i).completed(Boolean.TRUE.equals(sb.completed))
                    .updatedAt(Instant.now()).build());
        }
        blockRepository.saveAll(rebuilt);
        page.setUpdatedAt(Instant.now());
        Page saved = pageRepository.save(page);

        snapshot(saved, actor, "after restore from v" + versionNumber);
        return toResponse(saved);
    }

    // -------------- Internals --------------

    private void snapshot(Page page, User actor, String reason) {
        // Ponytail: hard cap at 50 snapshots/page to avoid blowing up H2 on noisy collab sessions.
        long count = versionRepository.countByPage(page);
        if (count >= 50) return;

        PageVersion top = versionRepository.findTopByPageOrderByVersionNumberDesc(page).orElse(null);
        int next = top == null ? 1 : top.getVersionNumber() + 1;

        List<SimpleBlock> current = blockRepository.findByPageIdOrderByPositionAsc(page.getId()).stream()
                .map(b -> new SimpleBlock(b.getType(),
                        b.getContent() == null ? "" : b.getContent(),
                        b.getPosition(),
                        Boolean.TRUE.equals(b.getCompleted())))
                .toList();

        versionRepository.save(PageVersion.builder()
                .page(page)
                .versionNumber(next)
                .title(page.getTitle() == null ? "" : page.getTitle())
                .content(page.getContent() == null ? "" : page.getContent())
                .blocksSnapshot(serializeBlocks(current))
                .author(actor)
                .createdAt(Instant.now())
                .build());
    }

    public Page getPageOrThrow(Long id) {
        return pageRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Page not found"));
    }

    private void replaceBlocks(Page page, List<BlockRequest> requests) {
        blockRepository.deleteByPageId(page.getId());
        List<Block> blocks = new ArrayList<>();
        StringBuilder legacyContent = new StringBuilder();
        int pos = 0;
        for (BlockRequest req : requests) {
            String type = normalizeType(req.getType());
            String content = req.getContent() == null ? "" : req.getContent();
            boolean completed = Boolean.TRUE.equals(req.getCompleted());
            blocks.add(Block.builder().page(page).type(type).content(content).position(pos)
                    .completed(completed).updatedAt(Instant.now()).build());
            if (!content.isBlank()) {
                if (legacyContent.length() > 0) legacyContent.append("\n");
                legacyContent.append(content);
            }
            pos++;
        }
        blockRepository.saveAll(blocks);
        page.setContent(legacyContent.toString());
    }

    private String normalizeType(String type) {
        if (type == null) return "paragraph";
        return switch (type.toLowerCase()) {
            case "paragraph", "heading", "todo", "bullet", "quote", "code", "divider" -> type.toLowerCase();
            default -> "paragraph";
        };
    }

    private PageResponse toResponse(Page p) {
        List<BlockResponse> blocks = blockRepository.findByPageIdOrderByPositionAsc(p.getId()).stream()
                .map(b -> BlockResponse.builder().id(b.getId()).type(b.getType()).content(b.getContent())
                        .position(b.getPosition()).completed(b.getCompleted()).build()).toList();
        return PageResponse.builder().id(p.getId()).title(p.getTitle()).content(p.getContent())
                .workspaceId(p.getWorkspace().getId())
                .parentId(p.getParent() != null ? p.getParent().getId() : null)
                .createdByName(p.getCreatedBy().getName()).createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt()).blocks(blocks).build();
    }

    // ───────── Snapshot encoding ─────────
    // Ponytail: avoid adding Jackson for a single field. Encode a tiny struct ourselves.
    private record SimpleBlock(String type, String content, Integer position, Boolean completed) {}

    private String serializeBlocks(List<SimpleBlock> blocks) {
        StringBuilder sb = new StringBuilder();
        for (SimpleBlock b : blocks) {
            sb.append(b.type == null ? "paragraph" : b.type).append('\u001f');
            sb.append(b.content == null ? "" : b.content).append('\u001e');
            sb.append(b.completed == null ? "0" : (b.completed ? "1" : "0")).append('\u001d');
        }
        return sb.toString();
    }

    private List<SimpleBlock> blocksFromSnapshot(String raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        // Split on \u001d (record separator) which never appears in normal text.
        String[] records = raw.split("\u001d", -1);
        List<SimpleBlock> out = new ArrayList<>();
        int pos = 0;
        for (String rec : records) {
            if (rec.isEmpty()) continue;
            // Last field may have a trailing \u001e; trim it.
            String[] parts = rec.split("\u001f", 2);
            if (parts.length < 2) continue;
            String[] second = parts[1].split("\u001e", 2);
            String type = parts[0];
            String content = second[0];
            boolean completed = second.length > 1 && second[1].startsWith("1");
            out.add(new SimpleBlock(type, content, pos++, completed));
        }
        return out;
    }
}
