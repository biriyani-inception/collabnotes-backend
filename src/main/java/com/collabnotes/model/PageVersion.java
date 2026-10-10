package com.collabnotes.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "page_versions", indexes = {
        @Index(name = "idx_pageversion_page", columnList = "page_id"),
        @Index(name = "idx_pageversion_page_num", columnList = "page_id, versionNumber")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "page_id", nullable = false)
    private Page page;

    @Column(nullable = false)
    private Integer versionNumber;

    @Column(nullable = false)
    private String title;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String content;

    // Snapshot of the blocks at the time of save. Stored as JSON-ish block list,
    // but we keep it as a comma+bar separated string to avoid pulling in Jackson into
    // the entity (the deserialiser lives in PageVersionRepository / page service).
    @Lob
    @Column(columnDefinition = "CLOB")
    private String blocksSnapshot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @Builder.Default
    private Instant createdAt = Instant.now();
}
