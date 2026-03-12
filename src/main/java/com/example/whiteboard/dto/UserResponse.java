package com.example.whiteboard.dto;

import lombok.Builder;

import java.util.UUID;

/**
 * Response DTO for GET /api/v1/auth/me
 * Only exposes what the frontend needs — never the internal providerId.
 */
@Builder
public record UserResponse(
        UUID id,
        String email,
        String displayName,
        String avatarUrl,
        String provider
) {}

