package com.example.whiteboard.service;

import com.example.whiteboard.dto.CreateRoomRequest;
import com.example.whiteboard.dto.RoomResponse;
import com.example.whiteboard.model.AuthProvider;
import com.example.whiteboard.model.Room;
import com.example.whiteboard.model.RoomMembership;
import com.example.whiteboard.model.User;
import com.example.whiteboard.repository.DrawingEventRepository;
import com.example.whiteboard.repository.RoomMembershipRepository;
import com.example.whiteboard.repository.RoomRepository;
import com.example.whiteboard.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoomMembershipRepository membershipRepository;

    @Mock
    private DrawingEventRepository drawingEventRepository;

    @InjectMocks
    private RoomService roomService;

    @Test
    void createRoom_generatesInviteCodeAndOwnerMembership() {
        UUID userId = UUID.randomUUID();
        User owner = testUser(userId, "owner@example.com");

        when(userRepository.findById(userId)).thenReturn(Optional.of(owner));
        when(roomRepository.findByInviteCode(anyString())).thenReturn(Optional.empty());
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> {
            Room saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(membershipRepository.save(any(RoomMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoomResponse response = roomService.createRoom(new CreateRoomRequest("Study Room"), userId);

        assertNotNull(response.inviteCode());
        assertEquals(6, response.inviteCode().length());
        assertTrue(response.inviteCode().matches("[A-Z0-9]{6}"));
        assertTrue(response.isOwner());

        verify(membershipRepository).save(any(RoomMembership.class));
    }

    @Test
    void joinRoom_normalizesInputAndJoinsRoom() {
        UUID ownerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();

        User owner = testUser(ownerId, "owner@example.com");
        User member = testUser(userId, "member@example.com");

        Room room = Room.builder()
                .id(roomId)
                .name("Project Room")
                .inviteCode("ABC123")
                .owner(owner)
                .build();

        when(roomRepository.findByInviteCode("ABC123")).thenReturn(Optional.of(room));
        when(userRepository.findById(userId)).thenReturn(Optional.of(member));
        when(membershipRepository.existsByUserIdAndRoomId(userId, roomId)).thenReturn(false);
        when(membershipRepository.save(any(RoomMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoomResponse response = roomService.joinRoom(" abc123 ", userId);

        assertEquals(roomId, response.id());
        assertEquals("ABC123", response.inviteCode());
        assertFalse(response.isOwner());

        verify(roomRepository).findByInviteCode("ABC123");
        verify(membershipRepository).save(any(RoomMembership.class));
    }

    @Test
    void joinRoom_rejectsInvalidCodeFormat() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> roomService.joinRoom("ab-12", UUID.randomUUID())
        );

        assertEquals("Invalid invite code format", exception.getMessage());
        verifyNoInteractions(roomRepository, userRepository, membershipRepository);
    }

    private User testUser(UUID id, String email) {
        return User.builder()
                .id(id)
                .email(email)
                .displayName("Test User")
                .provider(AuthProvider.GITHUB)
                .providerId(id.toString())
                .build();
    }
}

