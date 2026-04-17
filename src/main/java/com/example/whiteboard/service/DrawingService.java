package com.example.whiteboard.service;

import com.example.whiteboard.dto.DrawingEventRequest;
import com.example.whiteboard.dto.DrawingEventResponse;
import com.example.whiteboard.model.DrawingEvent;
import com.example.whiteboard.model.DrawingEventType;
import com.example.whiteboard.model.Room;
import com.example.whiteboard.model.User;
import com.example.whiteboard.repository.DrawingEventRepository;
import com.example.whiteboard.repository.RoomMembershipRepository;
import com.example.whiteboard.repository.RoomRepository;
import com.example.whiteboard.repository.UserRepository;
import com.example.whiteboard.security.SecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Handles the full lifecycle of a real-time drawing event:
 *  1. Authenticate the sender (derive identity from the security context — never trust client-supplied identity).
 *  2. Authorise: verify the sender is a member of the target room.
 *  3. Validate: check event type and data payload.
 *  4. Persist: save the event to drawing_events so latecomers can replay history.
 *  5. Broadcast: push the enriched event to every subscriber of /topic/room/{roomId}.
 *
 * This keeps the @MessageMapping controller thin and this class independently testable.
 */
@Service
@RequiredArgsConstructor
public class DrawingService {

    private final DrawingEventRepository drawingEventRepository;
    private final RoomRepository roomRepository;
    private final RoomMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final SecurityService securityService;

    /**
     * Validate, persist, and broadcast a drawing event for the given room.
     *
     * @param roomId  the UUID of the target room (from the STOMP destination)
     * @param request the drawing payload sent by the client
     * @throws AccessDeniedException    if the user is not authenticated or not a room member
     * @throws IllegalArgumentException if the event type is unknown or the data payload is empty
     */
    @Transactional
    public void processDrawEvent(UUID roomId, DrawingEventRequest request) {

        // Step 1 – identity: never trust a client-supplied user id
        UUID userId = securityService.getCurrentUserId();
        if (userId == null) {
            throw new AccessDeniedException("Authentication required");
        }

        // Step 2 – authorisation: sender must be a member of the room
        if (!membershipRepository.existsByUserIdAndRoomId(userId, roomId)) {
            throw new AccessDeniedException("Not a member of room " + roomId);
        }

        // Step 3 – validate type (accept any casing, e.g. "stroke" == "STROKE")
        if (request.type() == null || request.type().isBlank()) {
            throw new IllegalArgumentException("Event type must not be blank");
        }

        DrawingEventType type;
        try {
            type = DrawingEventType.valueOf(request.type().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown event type: " + request.type());
        }

        // Step 3 – validate data
        if (request.data() == null || request.data().isEmpty()) {
            throw new IllegalArgumentException("Event data must not be empty");
        }

        // Step 4 – persist
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));

        DrawingEvent event = DrawingEvent.builder()
                .room(room)
                .userId(userId)
                .type(type)
                .data(request.data())
                .build();

        event = drawingEventRepository.save(event);

        // Step 5 – broadcast to everyone subscribed to /topic/room/{roomId}
        String senderName = userRepository.findById(userId)
                .map(User::getDisplayName)
                .orElse("Unknown");

        DrawingEventResponse response = DrawingEventResponse.builder()
                .id(event.getId())
                .userId(userId)
                .senderName(senderName)
                .type(event.getType().name())
                .data(event.getData())
                .timestamp(event.getTimestamp())
                .build();

        messagingTemplate.convertAndSend("/topic/room/" + roomId, response);
    }
}
