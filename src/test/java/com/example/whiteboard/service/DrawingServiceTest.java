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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DrawingService.
 *
 * These tests verify the full happy-path and every early-exit guard:
 *  - unauthenticated user             → AccessDeniedException
 *  - authenticated non-member         → AccessDeniedException
 *  - blank event type                 → IllegalArgumentException
 *  - unknown event type               → IllegalArgumentException
 *  - null data payload                → IllegalArgumentException
 *  - empty data payload               → IllegalArgumentException
 *  - valid STROKE event               → persisted + broadcast with correct fields
 *  - valid SHAPE event                → persisted + broadcast
 *  - lowercase type string            → accepted (case-insensitive)
 */
@ExtendWith(MockitoExtension.class)
class DrawingServiceTest {

    @Mock private DrawingEventRepository drawingEventRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private RoomMembershipRepository membershipRepository;
    @Mock private UserRepository userRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private SecurityService securityService;

    @InjectMocks private DrawingService drawingService;

    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private static final DrawingEventRequest VALID_STROKE = new DrawingEventRequest(
            "STROKE",
            Map.of("color", "#FF0000", "brushSize", 5, "points", "[{\"x\":10,\"y\":20}]")
    );

    // ---- happy path --------------------------------------------------------

    @Test
    void processDrawEvent_memberSendsStrokeEvent_persistedAndBroadcast() {
        User user = buildUser("Alice");
        Room room = buildRoom(user);
        DrawingEvent savedEvent = buildSavedEvent(room, DrawingEventType.STROKE, VALID_STROKE.data(), 1L);

        when(securityService.getCurrentUserId()).thenReturn(USER_ID);
        when(membershipRepository.existsByUserIdAndRoomId(USER_ID, ROOM_ID)).thenReturn(true);
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(drawingEventRepository.save(any(DrawingEvent.class))).thenReturn(savedEvent);

        drawingService.processDrawEvent(ROOM_ID, VALID_STROKE);

        // verify persistence
        ArgumentCaptor<DrawingEvent> eventCaptor = ArgumentCaptor.forClass(DrawingEvent.class);
        verify(drawingEventRepository).save(eventCaptor.capture());
        DrawingEvent captured = eventCaptor.getValue();
        assertThat(captured.getUserId()).isEqualTo(USER_ID);
        assertThat(captured.getType()).isEqualTo(DrawingEventType.STROKE);
        assertThat(captured.getData()).isEqualTo(VALID_STROKE.data());

        // verify broadcast
        ArgumentCaptor<DrawingEventResponse> broadcastCaptor =
                ArgumentCaptor.forClass(DrawingEventResponse.class);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/room/" + ROOM_ID), broadcastCaptor.capture());
        DrawingEventResponse broadcast = broadcastCaptor.getValue();
        assertThat(broadcast.userId()).isEqualTo(USER_ID);
        assertThat(broadcast.senderName()).isEqualTo("Alice");
        assertThat(broadcast.type()).isEqualTo("STROKE");
        assertThat(broadcast.data()).isEqualTo(VALID_STROKE.data());
        assertThat(broadcast.id()).isEqualTo(1L);
    }

    @Test
    void processDrawEvent_memberSendsShapeEvent_persistedAndBroadcast() {
        DrawingEventRequest shapeRequest = new DrawingEventRequest(
                "SHAPE",
                Map.of("shapeType", "RECTANGLE", "x", 10, "y", 20, "width", 100, "height", 50)
        );

        User user = buildUser("Bob");
        Room room = buildRoom(user);
        DrawingEvent savedEvent = buildSavedEvent(room, DrawingEventType.SHAPE, shapeRequest.data(), 2L);

        when(securityService.getCurrentUserId()).thenReturn(USER_ID);
        when(membershipRepository.existsByUserIdAndRoomId(USER_ID, ROOM_ID)).thenReturn(true);
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(drawingEventRepository.save(any(DrawingEvent.class))).thenReturn(savedEvent);

        drawingService.processDrawEvent(ROOM_ID, shapeRequest);

        verify(drawingEventRepository).save(argThat(e -> e.getType() == DrawingEventType.SHAPE));
        verify(messagingTemplate).convertAndSend(
                eq("/topic/room/" + ROOM_ID), any(DrawingEventResponse.class));
    }

    @Test
    void processDrawEvent_lowercaseType_acceptedAsCaseInsensitive() {
        DrawingEventRequest lowercase = new DrawingEventRequest(
                "stroke", Map.of("color", "#000000", "brushSize", 2));

        User user = buildUser("Carol");
        Room room = buildRoom(user);
        DrawingEvent savedEvent = buildSavedEvent(room, DrawingEventType.STROKE, lowercase.data(), 3L);

        when(securityService.getCurrentUserId()).thenReturn(USER_ID);
        when(membershipRepository.existsByUserIdAndRoomId(USER_ID, ROOM_ID)).thenReturn(true);
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(drawingEventRepository.save(any(DrawingEvent.class))).thenReturn(savedEvent);

        drawingService.processDrawEvent(ROOM_ID, lowercase);

        verify(drawingEventRepository).save(argThat(e -> e.getType() == DrawingEventType.STROKE));
    }

    // ---- authentication guard ---------------------------------------------

    @Test
    void processDrawEvent_unauthenticatedUser_throwsAccessDeniedException() {
        when(securityService.getCurrentUserId()).thenReturn(null);

        assertThatThrownBy(() -> drawingService.processDrawEvent(ROOM_ID, VALID_STROKE))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required");

        verifyNoInteractions(drawingEventRepository, messagingTemplate);
    }

    // ---- membership guard -------------------------------------------------

    @Test
    void processDrawEvent_nonMember_throwsAccessDeniedException() {
        when(securityService.getCurrentUserId()).thenReturn(USER_ID);
        when(membershipRepository.existsByUserIdAndRoomId(USER_ID, ROOM_ID)).thenReturn(false);

        assertThatThrownBy(() -> drawingService.processDrawEvent(ROOM_ID, VALID_STROKE))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Not a member");

        verifyNoInteractions(drawingEventRepository, messagingTemplate);
    }

    // ---- payload validation guards ----------------------------------------

    @Test
    void processDrawEvent_blankType_throwsIllegalArgumentException() {
        when(securityService.getCurrentUserId()).thenReturn(USER_ID);
        when(membershipRepository.existsByUserIdAndRoomId(USER_ID, ROOM_ID)).thenReturn(true);

        DrawingEventRequest bad = new DrawingEventRequest("   ", Map.of("x", 1));

        assertThatThrownBy(() -> drawingService.processDrawEvent(ROOM_ID, bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type");

        verifyNoInteractions(drawingEventRepository, messagingTemplate);
    }

    @Test
    void processDrawEvent_unknownType_throwsIllegalArgumentException() {
        when(securityService.getCurrentUserId()).thenReturn(USER_ID);
        when(membershipRepository.existsByUserIdAndRoomId(USER_ID, ROOM_ID)).thenReturn(true);

        DrawingEventRequest bad = new DrawingEventRequest("CIRCLE", Map.of("x", 1));

        assertThatThrownBy(() -> drawingService.processDrawEvent(ROOM_ID, bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown event type: CIRCLE");

        verifyNoInteractions(drawingEventRepository, messagingTemplate);
    }

    @Test
    void processDrawEvent_nullData_throwsIllegalArgumentException() {
        when(securityService.getCurrentUserId()).thenReturn(USER_ID);
        when(membershipRepository.existsByUserIdAndRoomId(USER_ID, ROOM_ID)).thenReturn(true);

        DrawingEventRequest bad = new DrawingEventRequest("STROKE", null);

        assertThatThrownBy(() -> drawingService.processDrawEvent(ROOM_ID, bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("data");

        verifyNoInteractions(drawingEventRepository, messagingTemplate);
    }

    @Test
    void processDrawEvent_emptyData_throwsIllegalArgumentException() {
        when(securityService.getCurrentUserId()).thenReturn(USER_ID);
        when(membershipRepository.existsByUserIdAndRoomId(USER_ID, ROOM_ID)).thenReturn(true);

        DrawingEventRequest bad = new DrawingEventRequest("STROKE", Map.of());

        assertThatThrownBy(() -> drawingService.processDrawEvent(ROOM_ID, bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("data");

        verifyNoInteractions(drawingEventRepository, messagingTemplate);
    }

    // ---- helpers ----------------------------------------------------------

    private User buildUser(String displayName) {
        return User.builder()
                .id(USER_ID)
                .displayName(displayName)
                .email(displayName.toLowerCase() + "@example.com")
                .build();
    }

    private Room buildRoom(User owner) {
        return Room.builder()
                .id(ROOM_ID)
                .name("Test Room")
                .inviteCode("TST001")
                .owner(owner)
                .build();
    }

    private DrawingEvent buildSavedEvent(Room room, DrawingEventType type,
                                         Map<String, Object> data, long id) {
        return DrawingEvent.builder()
                .id(id)
                .room(room)
                .userId(USER_ID)
                .type(type)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }
}
