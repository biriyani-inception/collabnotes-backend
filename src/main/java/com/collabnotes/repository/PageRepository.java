package com.collabnotes.repository;

import com.collabnotes.model.Page;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PageRepository extends JpaRepository<Page, Long> {
    List<Page> findByWorkspaceIdAndParentIsNull(Long workspaceId);
    List<Page> findByParentId(Long parentId);
    List<Page> findByWorkspaceId(Long workspaceId);
}
