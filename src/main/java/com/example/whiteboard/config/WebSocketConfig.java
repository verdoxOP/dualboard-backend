package com.example.whiteboard.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket + STOMP configuration for real-time drawing sync.
 *
 * Architecture (see LU1_ARCHITECTURE.md, Section 3):
 * - /ws: STOMP handshake endpoint (with SockJS fallback)
 * - /app: prefix for client → server messages (e.g., /app/draw/{roomId})
 * - /topic: prefix for server → client broadcasts (e.g., /topic/room/{roomId})
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Server → Client: messages broadcast to /topic/...
        config.enableSimpleBroker("/topic");

        // Client → Server: messages sent to /app/... are routed to @MessageMapping methods
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket handshake endpoint
        // SockJS fallback for browsers that don't support WebSocket
        registry.addEndpoint("/ws")
                .setAllowedOrigins("http://localhost:3000")
                .withSockJS();
    }
}

