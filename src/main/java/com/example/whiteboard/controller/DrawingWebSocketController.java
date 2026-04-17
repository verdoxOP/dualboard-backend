package com.example.whiteboard.controller;

import com.example.whiteboard.dto.DrawingErrorResponse;
import com.example.whiteboard.dto.DrawingEventRequest;
import com.example.whiteboard.service.DrawingService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.util.UUID;

/**
 * WebSocket/STOMP controller for real-time collaborative drawing.
 *
 * Flow overview:
 *  Client connects to:   /ws           (SockJS handshake, inherits HTTP session auth)
 *  Client subscribes to: /topic/room/{roomId}   (receives live draw events from all room members)
 *  Client subscribes to: /user/queue/errors      (receives personalised error frames)
 *  Client sends to:      /app/draw/{roomId}      (sends its own draw events)
 *
 * On success:  the event is validated, saved to DB, then broadcast to /topic/room/{roomId}.
 * On failure:  a structured DrawingErrorResponse is sent back only to the offending client
 *              via /user/queue/errors (never broadcast to the room).
 */
@Controller
@RequiredArgsConstructor
public class DrawingWebSocketController {

    private final DrawingService drawingService;

    /**
     * Entry point for client draw events.
     *
     * Receives:  STOMP SEND to /app/draw/{roomId}
     * Delegates: all validation, persistence, and broadcast to DrawingService.
     * Errors:    @MessageExceptionHandler methods below catch and route them.
     *
     * @param roomId  parsed from the STOMP destination path variable
     * @param request the drawing payload (type + data map) sent by the client
     */
    @MessageMapping("/draw/{roomId}")
    public void handleDraw(@DestinationVariable UUID roomId,
                           @Payload DrawingEventRequest request) {
        drawingService.processDrawEvent(roomId, request);
    }

    /**
     * Handles authorisation failures (unauthenticated user or non-member sending to a room).
     *
     * The error is sent exclusively to the user who triggered it at /user/queue/errors.
     * Other room members never see this message.
     */
    @MessageExceptionHandler(AccessDeniedException.class)
    @SendToUser("/queue/errors")
    public DrawingErrorResponse handleAccessDenied(AccessDeniedException ex) {
        return new DrawingErrorResponse("FORBIDDEN", ex.getMessage());
    }

    /**
     * Handles payload validation failures (unknown event type, empty data, etc.).
     *
     * The error is sent exclusively to the user who triggered it at /user/queue/errors.
     */
    @MessageExceptionHandler(IllegalArgumentException.class)
    @SendToUser("/queue/errors")
    public DrawingErrorResponse handleValidationError(IllegalArgumentException ex) {
        return new DrawingErrorResponse("BAD_REQUEST", ex.getMessage());
    }
}
