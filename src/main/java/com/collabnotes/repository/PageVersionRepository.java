package com.collabnotes.repository;

import com.collabnotes.model.Page;
import com.collabnotes.model.PageVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PageVersionRepository extends JpaRepository<PageVersion, Long> {
    List<PageVersion> findByPageOrderByVersionNumberDesc(Page page);
    Optional<PageVersion> findTopByPageOrderByVersionNumberDesc(Page page);
    Optional<PageVersion> findByPageAndVersionNumber(Page page, Integer versionNumber);
    long countByPage(Page page);
}
