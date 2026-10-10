package com.collabnotes.websocket;

import com.collabnotes.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * Validates the JWT passed as a ?token=... query param on the SockJS/WebSocket
 * handshake URL and stores the authenticated user's id/name in the WebSocket
 * session attributes so the STOMP layer can identify "who" is editing/present.
 */
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {

        List<String> token = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .get("token");

        if (token == null || token.isEmpty()) {
            return false; // reject handshake -> client must supply a valid token
        }

        try {
            String jwt = token.get(0);
            String email = jwtUtil.extractEmail(jwt);
            Long userId = jwtUtil.extractUserId(jwt);

            if (!jwtUtil.isTokenValid(jwt, email)) {
                return false;
            }

            attributes.put("userId", userId);
            attributes.put("email", email);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
