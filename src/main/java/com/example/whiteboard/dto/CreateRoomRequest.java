package com.example.whiteboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for POST /api/v1/rooms
 * Validates that the room name is present and reasonable length.
 */
public record CreateRoomRequest(
        @NotBlank(message = "Room name is required")
        @Size(min = 1, max = 100, message = "Room name must be between 1 and 100 characters")
        String name
) {}

