package com.collabnotes.websocket;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which users currently have a given page open, in memory.
 * Good enough for a single-instance deployment / college project demo.
 * For horizontal scaling, this would move into Redis (e.g. a SET per page
 * with TTL'd heartbeats) so all backend instances share the same view.
 */
@Service
public class PresenceService {

    // pageId -> (sessionId -> "userId:userName")
    private final Map<Long, Map<String, String>> pagePresence = new ConcurrentHashMap<>();
    // sessionId -> pageId, so we know which page to clean up on disconnect
    private final Map<String, Long> sessionToPage = new ConcurrentHashMap<>();

    public void join(Long pageId, String sessionId, Long userId, String userName) {
        pagePresence.computeIfAbsent(pageId, k -> new ConcurrentHashMap<>())
                .put(sessionId, userId + ":" + userName);
        sessionToPage.put(sessionId, pageId);
    }

    public Long leave(String sessionId) {
        Long pageId = sessionToPage.remove(sessionId);
        if (pageId != null) {
            Map<String, String> members = pagePresence.get(pageId);
            if (members != null) {
                members.remove(sessionId);
                if (members.isEmpty()) pagePresence.remove(pageId);
            }
        }
        return pageId;
    }

    public Set<String> activeUsers(Long pageId) {
        return Set.copyOf(pagePresence.getOrDefault(pageId, Map.of()).values());
    }
}
