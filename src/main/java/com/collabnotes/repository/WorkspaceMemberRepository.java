package com.collabnotes.repository;

import com.collabnotes.model.User;
import com.collabnotes.model.Workspace;
import com.collabnotes.model.WorkspaceMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {
    List<WorkspaceMember> findByUserId(Long userId);
    List<WorkspaceMember> findByWorkspaceId(Long workspaceId);
    Optional<WorkspaceMember> findByWorkspaceAndUser(Workspace workspace, User user);
    boolean existsByWorkspaceIdAndUserId(Long workspaceId, Long userId);
}
