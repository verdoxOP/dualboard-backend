package com.example.whiteboard.dto;

/**
 * Error frame sent back to a single WebSocket client via /user/queue/errors
 * when their draw event cannot be processed (authorization or validation failure).
 *
 * Clients should subscribe to /user/queue/errors to receive these.
 *
 * code    – machine-readable category: "FORBIDDEN" or "BAD_REQUEST"
 * message – human-readable description of what went wrong
 */
public record DrawingErrorResponse(String code, String message) {}
