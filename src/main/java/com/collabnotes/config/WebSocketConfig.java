package com.collabnotes.config;

import com.collabnotes.websocket.JwtHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Clients SUBSCRIBE to /topic/page/{id}; the server broadcasts here.
        registry.enableSimpleBroker("/topic");
        // Clients SEND to /app/page/{id}/edit etc; routed to @MessageMapping methods.
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = allowedOriginsCsv();
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(origins.length == 0 ? new String[]{"*"} : origins)
                .addInterceptors(jwtHandshakeInterceptor)
                .setHandshakeHandler(new UserHandshakeHandler())
                .withSockJS(); // fallback for browsers/proxies that block raw WS
    }

    private String[] allowedOriginsCsv() {
        String csv = allowedOrigins == null ? "" : allowedOrigins.trim();
        if (csv.isEmpty() || csv.equals("*")) return new String[]{"*"};
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    /** Assigns a STOMP Principal from the userId/email placed in session attributes
     *  by JwtHandshakeInterceptor, so @MessageMapping methods can identify the sender. */
    static class UserHandshakeHandler extends DefaultHandshakeHandler {
        @Override
        protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
                                           Map<String, Object> attributes) {
            Object email = attributes.get("email");
            Object userId = attributes.get("userId");
            String principalName = (email != null ? email : "anonymous") + "|" + userId;
            return () -> principalName;
        }
    }
}
