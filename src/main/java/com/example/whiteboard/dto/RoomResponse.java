package com.example.whiteboard.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for room operations.
 * Returned by create, join, list, and get-by-id endpoints.
 */
@Builder
public record RoomResponse(
        UUID id,
        String name,
        String inviteCode,
        Instant createdAt,
        boolean isOwner
) {}

