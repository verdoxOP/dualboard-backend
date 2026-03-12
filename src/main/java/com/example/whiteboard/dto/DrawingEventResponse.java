package com.example.whiteboard.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for drawing events returned by GET /api/v1/rooms/{id}/history.
 * Also used as the broadcast format over WebSocket (with senderName added).
 */
@Builder
public record DrawingEventResponse(
        Long id,
        UUID userId,
        String senderName,
        String type,
        Map<String, Object> data,
        Instant timestamp
) {}

