package com.collabnotes.service;

import com.collabnotes.dto.WorkspaceDtos.*;
import com.collabnotes.model.*;
import com.collabnotes.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final UserRepository userRepository;

    public WorkspaceResponse createWorkspace(Long ownerId, CreateWorkspaceRequest req) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Workspace workspace = Workspace.builder()
                .name(req.getName())
                .owner(owner)
                .build();
        workspace = workspaceRepository.save(workspace);

        WorkspaceMember membership = WorkspaceMember.builder()
                .workspace(workspace)
                .user(owner)
                .role(Role.OWNER)
                .build();
        memberRepository.save(membership);

        return toResponse(workspace, Role.OWNER);
    }

    public List<WorkspaceResponse> listMyWorkspaces(Long userId) {
        return memberRepository.findByUserId(userId).stream()
                .map(m -> toResponse(m.getWorkspace(), m.getRole()))
                .toList();
    }

    public void inviteMember(Long workspaceId, Long requesterId, InviteMemberRequest req) {
        Workspace workspace = getWorkspaceOrThrow(workspaceId);
        requireRole(workspace, requesterId, Role.OWNER);

        Role role;
        try {
            role = Role.valueOf(req.getRole().toUpperCase());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Role must be EDITOR or VIEWER");
        }
        if (role == Role.OWNER) {
            throw new IllegalArgumentException("Cannot invite a second owner");
        }

        User invitee = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No user is registered with that email — ask them to sign up first"));

        if (memberRepository.existsByWorkspaceIdAndUserId(workspaceId, invitee.getId())) {
            throw new IllegalArgumentException("User is already a member of this workspace");
        }

        memberRepository.save(WorkspaceMember.builder()
                .workspace(workspace)
                .user(invitee)
                .role(role)
                .build());
    }

    /** Lists every member of the workspace. Membership required (any role). */
    public List<com.collabnotes.dto.MemberDtos.MemberResponse> listMembers(Long workspaceId, Long requesterId) {
        Workspace workspace = getWorkspaceOrThrow(workspaceId);
        requireMembership(workspace, requesterId);
        return memberRepository.findByWorkspaceId(workspaceId).stream()
                .map(m -> com.collabnotes.dto.MemberDtos.MemberResponse.builder()
                        .userId(m.getUser().getId())
                        .name(m.getUser().getName())
                        .email(m.getUser().getEmail())
                        .role(m.getRole())
                        .build())
                .toList();
    }

    /** Updates a member's role. OWNER-only; the sole OWNER cannot demote themselves. */
    public void updateMemberRole(Long workspaceId, Long requesterId, Long targetUserId, String newRole) {
        Workspace workspace = getWorkspaceOrThrow(workspaceId);
        requireRole(workspace, requesterId, Role.OWNER);

        Role role;
        try {
            role = Role.valueOf(newRole == null ? "" : newRole.toUpperCase());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Role must be EDITOR or VIEWER");
        }

        WorkspaceMember target = memberRepository.findByWorkspaceAndUser(workspace, userRepository.getReferenceById(targetUserId))
                .orElseThrow(() -> new IllegalArgumentException("That user is not a member of this workspace"));

        // Ponytail: don't allow promoting anybody to OWNER from this endpoint.
        if (role == Role.OWNER) {
            throw new IllegalArgumentException("Cannot promote to OWNER through this endpoint");
        }
        // Last-Owner guard: refuse to demote the last OWNER.
        if (target.getRole() == Role.OWNER) {
            long ownerCount = memberRepository.findByWorkspaceId(workspaceId).stream()
                    .filter(m -> m.getRole() == Role.OWNER).count();
            if (ownerCount <= 1) {
                throw new IllegalArgumentException(
                        "Cannot demote the last OWNER — promote another member to OWNER first");
            }
        }

        target.setRole(role);
        memberRepository.save(target);
    }

    /** Removes a member. OWNER-only; the last OWNER cannot be removed. */
    public void removeMember(Long workspaceId, Long requesterId, Long targetUserId) {
        Workspace workspace = getWorkspaceOrThrow(workspaceId);
        requireRole(workspace, requesterId, Role.OWNER);

        // Self-remove is technically OK, but we still need the last-Owner guard.
        long ownerCount = memberRepository.findByWorkspaceId(workspaceId).stream()
                .filter(m -> m.getRole() == Role.OWNER).count();

        WorkspaceMember target = memberRepository.findByWorkspaceAndUser(workspace, userRepository.getReferenceById(targetUserId))
                .orElseThrow(() -> new IllegalArgumentException("That user is not a member of this workspace"));

        if (target.getRole() == Role.OWNER && ownerCount <= 1) {
            throw new IllegalArgumentException(
                    "Cannot remove the last OWNER — promote another member first");
        }

        memberRepository.delete(target);
    }

    public Workspace getWorkspaceOrThrow(Long id) {
        return workspaceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found"));
    }

    /** Returns the caller's role in the workspace, or throws if they are not a member. */
    public Role requireMembership(Workspace workspace, Long userId) {
        return memberRepository.findByWorkspaceAndUser(workspace, userRepository.getReferenceById(userId))
                .orElseThrow(() -> new SecurityException("You do not have access to this workspace"))
                .getRole();
    }

    /** Throws unless the caller's role is at least as privileged as required. */
    public void requireRole(Workspace workspace, Long userId, Role minimumRole) {
        Role role = requireMembership(workspace, userId);
        if (!hasAtLeast(role, minimumRole)) {
            throw new SecurityException("Insufficient permissions for this action");
        }
    }

    private boolean hasAtLeast(Role actual, Role required) {
        // OWNER > EDITOR > VIEWER
        return rank(actual) >= rank(required);
    }

    private int rank(Role role) {
        return switch (role) {
            case VIEWER -> 0;
            case EDITOR -> 1;
            case OWNER -> 2;
        };
    }

    private WorkspaceResponse toResponse(Workspace w, Role myRole) {
        return WorkspaceResponse.builder()
                .id(w.getId())
                .name(w.getName())
                .ownerId(w.getOwner().getId())
                .ownerName(w.getOwner().getName())
                .myRole(myRole)
                .build();
    }
}
