package com.example.whiteboard.controller;

import com.example.whiteboard.dto.CreateRoomRequest;
import com.example.whiteboard.dto.DrawingEventResponse;
import com.example.whiteboard.dto.RoomResponse;
import com.example.whiteboard.security.SecurityService;
import com.example.whiteboard.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Room management controller (US-2.1, US-2.2, US-2.3).
 *
 * Security model (ADR-7):
 * - All endpoints require authentication (enforced by SecurityConfig)
 * - Room-specific endpoints use @PreAuthorize with SecurityService.isMember()
 * - This keeps business logic in RoomService and security logic in SecurityService
 */
@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final SecurityService securityService;

    /**
     * POST /api/v1/rooms
     * Create a new whiteboard room. The authenticated user becomes the owner.
     */
    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        UUID userId = securityService.getCurrentUserId();
        RoomResponse room = roomService.createRoom(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(room);
    }

    /**
     * POST /api/v1/rooms/join/{inviteCode}
     * Join an existing room by invite code.
     *
     * Returns 404 if code is invalid, 409 if already a member.
     */
    @PostMapping("/join/{inviteCode}")
    public ResponseEntity<?> joinRoom(@PathVariable String inviteCode) {
        UUID userId = securityService.getCurrentUserId();
        try {
            RoomResponse room = roomService.joinRoom(inviteCode, userId);
            return ResponseEntity.ok(room);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/v1/rooms
     * List all rooms the authenticated user owns or is a member of.
     */
    @GetMapping
    public ResponseEntity<List<RoomResponse>> listRooms() {
        UUID userId = securityService.getCurrentUserId();
        List<RoomResponse> rooms = roomService.getUserRooms(userId);
        return ResponseEntity.ok(rooms);
    }

    /**
     * GET /api/v1/rooms/{roomId}
     * Get details for a specific room. Requires room membership (ADR-7).
     */
    @GetMapping("/{roomId}")
    @PreAuthorize("@securityService.isMember(#roomId)")
    public ResponseEntity<RoomResponse> getRoom(@PathVariable UUID roomId) {
        UUID userId = securityService.getCurrentUserId();
        RoomResponse room = roomService.getRoom(roomId, userId);
        return ResponseEntity.ok(room);
    }

    /**
     * GET /api/v1/rooms/{roomId}/history
     * Fetch all drawing events for a room — "Full State on Join" (ADR-6).
     * Requires room membership (ADR-7).
     */
    @GetMapping("/{roomId}/history")
    @PreAuthorize("@securityService.isMember(#roomId)")
    public ResponseEntity<List<DrawingEventResponse>> getRoomHistory(@PathVariable UUID roomId) {
        List<DrawingEventResponse> history = roomService.getRoomHistory(roomId);
        return ResponseEntity.ok(history);
    }
}

