package com.example.whiteboard.dto;

import com.example.whiteboard.model.AuthProvider;
import java.util.UUID;

/**
 * Response DTO for GET /api/v1/auth/me
 * Only exposes what the frontend needs — never the internal providerId.
 * Provides safe user details to the frontend without exposing internals.
 */
public record UserResponse(
        UUID id,
        String email,
        String displayName,
        String avatarUrl,
        AuthProvider provider
) {}
