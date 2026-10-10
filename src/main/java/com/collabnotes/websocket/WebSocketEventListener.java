package com.collabnotes.websocket;

import com.collabnotes.dto.CollabDtos.BroadcastMessage;
import com.collabnotes.dto.CollabDtos.EventType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final PresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        Long pageId = presenceService.leave(sessionId);
        if (pageId != null) {
            messagingTemplate.convertAndSend("/topic/page/" + pageId,
                    BroadcastMessage.builder()
                            .type(EventType.PRESENCE_LEAVE)
                            .pageId(pageId)
                            .timestamp(System.currentTimeMillis())
                            .build());
        }
    }
}
