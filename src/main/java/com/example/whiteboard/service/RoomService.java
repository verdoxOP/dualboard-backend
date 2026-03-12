package com.example.whiteboard.service;

import com.example.whiteboard.dto.CreateRoomRequest;
import com.example.whiteboard.dto.DrawingEventResponse;
import com.example.whiteboard.dto.RoomResponse;
import com.example.whiteboard.model.*;
import com.example.whiteboard.repository.DrawingEventRepository;
import com.example.whiteboard.repository.RoomMembershipRepository;
import com.example.whiteboard.repository.RoomRepository;
import com.example.whiteboard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

/**
 * Business logic for room management (Iteration 1 stories: US-2.1, US-2.2, US-2.3).
 *
 * This service handles room CRUD and membership. Security checks are NOT done here —
 * they are handled declaratively via @PreAuthorize on the controller layer (ADR-7).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomService {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final RoomMembershipRepository membershipRepository;
    private final DrawingEventRepository drawingEventRepository;

    private static final String INVITE_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int INVITE_CODE_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Create a new room and make the creator both the owner and a member.
     * (US-2.1: "Room created with a unique code, persisted in PostgreSQL,
     * creator automatically joins the room")
     */
    @Transactional
    public RoomResponse createRoom(CreateRoomRequest request, UUID userId) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Room room = Room.builder()
                .name(request.name())
                .inviteCode(generateUniqueInviteCode())
                .owner(owner)
                .build();

        room = roomRepository.save(room);

        // Creator is automatically a member with OWNER role
        RoomMembership membership = RoomMembership.builder()
                .user(owner)
                .room(room)
                .role(RoomRole.OWNER)
                .build();

        membershipRepository.save(membership);

        return toResponse(room, true);
    }

    /**
     * Join an existing room by invite code.
     * (US-2.2: "Valid code connects user to the room; invalid code shows error")
     *
     * @throws IllegalArgumentException if room not found
     * @throws IllegalStateException if user is already a member
     */
    @Transactional
    public RoomResponse joinRoom(String inviteCode, UUID userId) {
        Room room = roomRepository.findByInviteCode(inviteCode.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Check for duplicate membership
        if (membershipRepository.existsByUserIdAndRoomId(userId, room.getId())) {
            throw new IllegalStateException("Already a member of this room");
        }

        RoomMembership membership = RoomMembership.builder()
                .user(user)
                .room(room)
                .role(RoomRole.MEMBER)
                .build();

        membershipRepository.save(membership);

        boolean isOwner = room.getOwner().getId().equals(userId);
        return toResponse(room, isOwner);
    }

    /**
     * List all rooms the user is a member of.
     * (US-2.3: "Rooms listed on dashboard after login, each showing name and last modified date")
     */
    public List<RoomResponse> getUserRooms(UUID userId) {
        List<RoomMembership> memberships = membershipRepository.findByUserId(userId);

        return memberships.stream()
                .map(membership -> toResponse(
                        membership.getRoom(),
                        membership.getRole() == RoomRole.OWNER
                ))
                .toList();
    }

    /**
     * Get a single room by ID.
     * Authorization is handled by @PreAuthorize on the controller.
     */
    public RoomResponse getRoom(UUID roomId, UUID userId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        boolean isOwner = room.getOwner().getId().equals(userId);
        return toResponse(room, isOwner);
    }

    /**
     * Fetch all drawing events for a room — the "Full State on Join" query (ADR-6).
     * Returns events in chronological order so the frontend can replay them to
     * reconstruct the canvas.
     */
    public List<DrawingEventResponse> getRoomHistory(UUID roomId) {
        List<DrawingEvent> events = drawingEventRepository.findByRoomIdOrderByTimestampAsc(roomId);

        return events.stream()
                .map(this::toDrawingEventResponse)
                .toList();
    }

    // ---- Private helpers ----

    private RoomResponse toResponse(Room room, boolean isOwner) {
        return RoomResponse.builder()
                .id(room.getId())
                .name(room.getName())
                .inviteCode(room.getInviteCode())
                .createdAt(room.getCreatedAt())
                .isOwner(isOwner)
                .build();
    }

    private DrawingEventResponse toDrawingEventResponse(DrawingEvent event) {
        // For history, we don't include senderName to keep the query simple.
        // The frontend can map userId → name from room membership data.
        return DrawingEventResponse.builder()
                .id(event.getId())
                .userId(event.getUserId())
                .type(event.getType().name())
                .data(event.getData())
                .timestamp(event.getTimestamp())
                .build();
    }

    /**
     * Generate a unique 6-character alphanumeric invite code.
     * Uses SecureRandom to prevent predictability.
     * Retries if a collision occurs (extremely unlikely with 36^6 = 2.1B combinations).
     */
    private String generateUniqueInviteCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(INVITE_CODE_LENGTH);
            for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
                sb.append(INVITE_CODE_CHARS.charAt(RANDOM.nextInt(INVITE_CODE_CHARS.length())));
            }
            code = sb.toString();
        } while (roomRepository.findByInviteCode(code).isPresent());

        return code;
    }
}

