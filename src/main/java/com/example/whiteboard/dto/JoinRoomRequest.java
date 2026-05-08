package com.example.whiteboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for POST /api/v1/rooms/join.
 */
public record JoinRoomRequest(
        @NotBlank(message = "Invite code is required")
        @Size(min = 6, max = 6, message = "Invite code must be 6 characters")
        String inviteCode
) {}

