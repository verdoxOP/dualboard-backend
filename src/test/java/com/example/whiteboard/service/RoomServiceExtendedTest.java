package com.example.whiteboard.service;

import com.example.whiteboard.dto.CreateRoomRequest;
import com.example.whiteboard.dto.RoomResponse;
import com.example.whiteboard.model.AuthProvider;
import com.example.whiteboard.model.DrawingEvent;
import com.example.whiteboard.model.Room;
import com.example.whiteboard.model.RoomMembership;
import com.example.whiteboard.model.RoomRole;
import com.example.whiteboard.model.User;
import com.example.whiteboard.repository.DrawingEventRepository;
import com.example.whiteboard.repository.RoomMembershipRepository;
import com.example.whiteboard.repository.RoomRepository;
import com.example.whiteboard.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Extended unit tests for RoomService covering additional scenarios.
 */
@ExtendWith(MockitoExtension.class)
class RoomServiceExtendedTest {

    @Mock private RoomRepository roomRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoomMembershipRepository membershipRepository;
    @Mock private DrawingEventRepository drawingEventRepository;

    @InjectMocks private RoomService roomService;

    private UUID userId;
    private UUID roomId;
    private User testUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        roomId = UUID.randomUUID();
        testUser = User.builder()
                .id(userId)
                .email("test@example.com")
                .displayName("Test User")
                .provider(AuthProvider.GITHUB)
                .providerId("123456")
                .build();
    }

    // ---- getUserRooms() ----

    @Test
    void getUserRooms_userWithMultipleRooms_returnsAll() {
        Room room1 = buildRoom(roomId, "Room 1", testUser);
        Room room2 = buildRoom(UUID.randomUUID(), "Room 2", testUser);
        
        RoomMembership membership1 = RoomMembership.builder()
                .user(testUser)
                .room(room1)
                .role(RoomRole.OWNER)
                .build();
        RoomMembership membership2 = RoomMembership.builder()
                .user(testUser)
                .room(room2)
                .role(RoomRole.MEMBER)
                .build();

        when(membershipRepository.findByUserId(userId)).thenReturn(List.of(membership1, membership2));

        List<RoomResponse> result = roomService.getUserRooms(userId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).isOwner()).isTrue();
        assertThat(result.get(1).isOwner()).isFalse();
    }

    @Test
    void getUserRooms_userWithNoRooms_returnsEmptyList() {
        when(membershipRepository.findByUserId(userId)).thenReturn(List.of());

        List<RoomResponse> result = roomService.getUserRooms(userId);

        assertThat(result).isEmpty();
    }

    // ---- getRoom() ----

    @Test
    void getRoom_roomOwner_returnsResponseWithOwnerFlag() {
        Room room = buildRoom(roomId, "Test Room", testUser);
        
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        RoomResponse result = roomService.getRoom(roomId, userId);

        assertThat(result.isOwner()).isTrue();
        assertThat(result.id()).isEqualTo(roomId);
    }

    @Test
    void getRoom_notOwner_returnsResponseWithoutOwnerFlag() {
        UUID ownerId = UUID.randomUUID();
        User owner = User.builder()
                .id(ownerId)
                .email("owner@example.com")
                .displayName("Owner")
                .provider(AuthProvider.GITHUB)
                .providerId("999")
                .build();
        
        Room room = buildRoom(roomId, "Test Room", owner);
        
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        RoomResponse result = roomService.getRoom(roomId, userId);

        assertThat(result.isOwner()).isFalse();
    }

    @Test
    void getRoom_nonExistentRoom_throwsException() {
        when(roomRepository.findById(roomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.getRoom(roomId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Room not found");
    }

    // ---- getRoomHistory() ----

    @Test
    void getRoomHistory_withEvents_returnsEventsInChronologicalOrder() {
        Room room = buildRoom(roomId, "Test Room", testUser);
        Instant now = Instant.now();
        
        DrawingEvent event1 = DrawingEvent.builder()
                .id(1L)
                .room(room)
                .userId(userId)
                .type(com.example.whiteboard.model.DrawingEventType.STROKE)
                .data(java.util.Map.of("x", 10))
                .timestamp(now.minusSeconds(10))
                .build();
        
        DrawingEvent event2 = DrawingEvent.builder()
                .id(2L)
                .room(room)
                .userId(userId)
                .type(com.example.whiteboard.model.DrawingEventType.SHAPE)
                .data(java.util.Map.of("type", "RECTANGLE"))
                .timestamp(now)
                .build();

        when(drawingEventRepository.findByRoomIdOrderByTimestampAsc(roomId))
                .thenReturn(List.of(event1, event2));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        var result = roomService.getRoomHistory(roomId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(1).id()).isEqualTo(2L);
    }

    @Test
    void getRoomHistory_emptyRoom_returnsEmptyList() {
        when(drawingEventRepository.findByRoomIdOrderByTimestampAsc(roomId)).thenReturn(List.of());

        var result = roomService.getRoomHistory(roomId);

        assertThat(result).isEmpty();
    }

    @Test
    void getRoomHistory_eventByUnknownUser_setsDefaultDisplayName() {
        Room room = buildRoom(roomId, "Test Room", testUser);
        UUID unknownUserId = UUID.randomUUID();
        
        DrawingEvent event = DrawingEvent.builder()
                .id(1L)
                .room(room)
                .userId(unknownUserId)
                .type(com.example.whiteboard.model.DrawingEventType.STROKE)
                .data(java.util.Map.of("x", 10))
                .timestamp(Instant.now())
                .build();

        when(drawingEventRepository.findByRoomIdOrderByTimestampAsc(roomId)).thenReturn(List.of(event));
        when(userRepository.findById(unknownUserId)).thenReturn(Optional.empty());

        var result = roomService.getRoomHistory(roomId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).senderName()).isEqualTo("Unknown");
    }

    // ---- createRoom() - edge cases ----

    @Test
    void createRoom_userNotFound_throwsException() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        CreateRoomRequest request = new CreateRoomRequest("Test Room");

        assertThatThrownBy(() -> roomService.createRoom(request, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void createRoom_inviteCodeIsUnique() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        
        // First call returns empty (collision), second returns empty (unique)
        when(roomRepository.findByInviteCode(anyString()))
                .thenReturn(Optional.empty());
        
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> {
            Room saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        
        when(membershipRepository.save(any(RoomMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateRoomRequest request = new CreateRoomRequest("New Room");
        RoomResponse response1 = roomService.createRoom(request, userId);
        RoomResponse response2 = roomService.createRoom(request, userId);

        assertThat(response1.inviteCode()).isNotEqualTo(response2.inviteCode());
    }

    // ---- joinRoom() - edge cases ----

    @Test
    void joinRoom_uppercaseAndWhitespace_normalizes() {
        User member = User.builder()
                .id(UUID.randomUUID())
                .email("member@example.com")
                .displayName("Member")
                .provider(AuthProvider.GITHUB)
                .providerId("777")
                .build();
        
        Room room = buildRoom(roomId, "Test Room", testUser);
        room.setInviteCode("ABC123");  // Set the invite code to what we're mocking

        when(roomRepository.findByInviteCode("ABC123")).thenReturn(Optional.of(room));
        when(userRepository.findById(userId)).thenReturn(Optional.of(member));
        when(membershipRepository.existsByUserIdAndRoomId(userId, roomId)).thenReturn(false);
        when(membershipRepository.save(any(RoomMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RoomResponse result = roomService.joinRoom("  abc123  ", userId);

        assertThat(result.inviteCode()).isEqualTo("ABC123");
        verify(roomRepository).findByInviteCode("ABC123");
    }

    @Test
    void joinRoom_nullInviteCode_throwsException() {
        assertThatThrownBy(() -> roomService.joinRoom(null, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invite code is required");
    }

    @Test
    void joinRoom_userNotFound_throwsException() {
        Room room = buildRoom(roomId, "Test Room", testUser);
        
        when(roomRepository.findByInviteCode("ABC123")).thenReturn(Optional.of(room));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.joinRoom("ABC123", userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void joinRoom_alreadyMember_throwsException() {
        User member = User.builder()
                .id(userId)
                .email("member@example.com")
                .displayName("Member")
                .provider(AuthProvider.GITHUB)
                .providerId("777")
                .build();
        
        Room room = buildRoom(roomId, "Test Room", testUser);

        when(roomRepository.findByInviteCode("ABC123")).thenReturn(Optional.of(room));
        when(userRepository.findById(userId)).thenReturn(Optional.of(member));
        when(membershipRepository.existsByUserIdAndRoomId(userId, roomId)).thenReturn(true);

        assertThatThrownBy(() -> roomService.joinRoom("ABC123", userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Already a member");
    }

    // ---- Helper ----

    private Room buildRoom(UUID id, String name, User owner) {
        return Room.builder()
                .id(id)
                .name(name)
                .inviteCode("TST" + System.nanoTime() % 1000)
                .owner(owner)
                .createdAt(Instant.now())
                .build();
    }
}
