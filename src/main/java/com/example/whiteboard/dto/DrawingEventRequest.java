package com.example.whiteboard.dto;

import java.util.Map;

/**
 * Incoming WebSocket message from the client.
 * The client sends ONLY type + data. The server enriches with
 * userId, senderName, and timestamp before broadcasting (ADR-3).
 */
public record DrawingEventRequest(
        String type,
        Map<String, Object> data
) {}

