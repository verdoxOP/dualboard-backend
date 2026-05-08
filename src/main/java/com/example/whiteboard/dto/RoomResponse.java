package com.example.whiteboard.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for room operations.
 * Returned by create, join, list, and get-by-id endpoints.
 */
public record RoomResponse(
        UUID id,
        String name,
        String inviteCode,
        boolean isOwner,
        Instant createdAt
) {}
