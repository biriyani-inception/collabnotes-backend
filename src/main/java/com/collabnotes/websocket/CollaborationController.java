package com.collabnotes.websocket;

import com.collabnotes.dto.CollabDtos.*;
import com.collabnotes.dto.PageDtos.UpdatePageRequest;
import com.collabnotes.model.Page;
import com.collabnotes.model.Role;
import com.collabnotes.model.User;
import com.collabnotes.repository.UserRepository;
import com.collabnotes.service.PageService;
import com.collabnotes.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class CollaborationController {
    private final SimpMessagingTemplate messagingTemplate;
    private final PageService pageService;
    private final WorkspaceService workspaceService;
    private final UserRepository userRepository;
    private final PresenceService presenceService;

    @MessageMapping("/page/{pageId}/edit")
    public void handleEdit(@DestinationVariable Long pageId, EditMessage message, Principal principal,
                           SimpMessageHeaderAccessor headerAccessor) {
        UserInfo user = resolveUser(principal, headerAccessor);
        String sessionId = headerAccessor.getSessionId();

        Role role;
        try {
            role = accessRole(pageId, user.id());
        } catch (SecurityException ex) {
            // Silently drop — the broker never sends back an error frame; surface via no-op.
            return;
        }

        switch (message.getType()) {
            case CONTENT_UPDATE -> {
                if (role == Role.VIEWER) return; // drop viewer edits, allow presence
                pageService.updatePage(pageId, user.id(), new UpdatePageRequest(null, message.getContent(), null));
                broadcast(pageId, EventType.CONTENT_UPDATE, user, message.getContent(), null, null);
            }
            case BLOCKS_UPDATE -> {
                if (role == Role.VIEWER) return;
                pageService.updatePage(pageId, user.id(), new UpdatePageRequest(null, null, message.getBlocks()));
                broadcast(pageId, EventType.BLOCKS_UPDATE, user, null, null, message.getBlocks());
            }
            case CURSOR_MOVE -> broadcast(pageId, EventType.CURSOR_MOVE, user, null, message.getCursorPos(), null);
            case TYPING -> broadcast(pageId, EventType.TYPING, user, null, null, null);
            case PRESENCE_JOIN -> {
                presenceService.join(pageId, sessionId, user.id(), user.name());
                broadcast(pageId, EventType.PRESENCE_JOIN, user, null, null, null);
            }
            case PRESENCE_LEAVE -> {
                presenceService.leave(sessionId);
                broadcast(pageId, EventType.PRESENCE_LEAVE, user, null, null, null);
            }
        }
    }

    private Role accessRole(Long pageId, Long userId) {
        Page page = pageService.getPageOrThrow(pageId);
        return workspaceService.requireMembership(page.getWorkspace(), userId);
    }

    private void broadcast(Long pageId, EventType type, UserInfo user, String content, Integer cursorPos,
                           java.util.List<com.collabnotes.dto.PageDtos.BlockRequest> blocks) {
        BroadcastMessage msg = BroadcastMessage.builder().type(type).pageId(pageId).userId(user.id())
                .userName(user.name()).content(content).cursorPos(cursorPos).blocks(blocks)
                .timestamp(System.currentTimeMillis()).build();
        messagingTemplate.convertAndSend("/topic/page/" + pageId, msg);
    }

    private UserInfo resolveUser(Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        Object userIdAttr = headerAccessor.getSessionAttributes().get("userId");

        if (userIdAttr == null) {
            throw new IllegalStateException("No authenticated user on WebSocket session");
        }

        Long userId = Long.valueOf(userIdAttr.toString());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Unknown user on WebSocket session"));

        return new UserInfo(user.getId(), user.getName());
    }

    private record UserInfo(Long id, String name) {}
}
