package com.collabnotes.repository;

import com.collabnotes.model.Block;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BlockRepository extends JpaRepository<Block, Long> {
    List<Block> findByPageIdOrderByPositionAsc(Long pageId);
    void deleteByPageId(Long pageId);
}
