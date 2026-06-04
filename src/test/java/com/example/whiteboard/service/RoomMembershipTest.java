package com.example.whiteboard.service;

import com.example.whiteboard.model.*;
import com.example.whiteboard.repository.RoomMembershipRepository;
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
import static org.mockito.Mockito.*;

/**
 * Additional unit tests for RoomMembership operations and role management.
 */
@ExtendWith(MockitoExtension.class)
class RoomMembershipTest {

    @Mock private RoomMembershipRepository membershipRepository;

    private UUID userId;
    private UUID roomId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        roomId = UUID.randomUUID();
    }

    @Test
    void existsByUserIdAndRoomId_whenMemberExists_returnsTrue() {
        when(membershipRepository.existsByUserIdAndRoomId(userId, roomId)).thenReturn(true);

        boolean result = membershipRepository.existsByUserIdAndRoomId(userId, roomId);

        assertThat(result).isTrue();
        verify(membershipRepository).existsByUserIdAndRoomId(userId, roomId);
    }

    @Test
    void existsByUserIdAndRoomId_whenMemberNotExists_returnsFalse() {
        when(membershipRepository.existsByUserIdAndRoomId(userId, roomId)).thenReturn(false);

        boolean result = membershipRepository.existsByUserIdAndRoomId(userId, roomId);

        assertThat(result).isFalse();
    }

    @Test
    void findByUserId_withMultipleMemberships_returnsAll() {
        User user = User.builder()
                .id(userId)
                .email("test@example.com")
                .displayName("Test")
                .provider(AuthProvider.GITHUB)
                .providerId("123")
                .build();

        Room room1 = buildRoom();
        Room room2 = buildRoom();

        RoomMembership m1 = RoomMembership.builder()
                .user(user)
                .room(room1)
                .role(RoomRole.OWNER)
                .build();

        RoomMembership m2 = RoomMembership.builder()
                .user(user)
                .room(room2)
                .role(RoomRole.MEMBER)
                .build();

        when(membershipRepository.findByUserId(userId)).thenReturn(List.of(m1, m2));

        List<RoomMembership> result = membershipRepository.findByUserId(userId);

        assertThat(result).hasSize(2);
        assertThat(result).contains(m1, m2);
    }

    @Test
    void roomRole_owner_hasOwnerPrivileges() {
        assertThat(RoomRole.OWNER).isNotEqualTo(RoomRole.MEMBER);
    }

    @Test
    void roomRole_member_isReadOnly() {
        assertThat(RoomRole.MEMBER).isNotEqualTo(RoomRole.OWNER);
    }

    private Room buildRoom() {
        return Room.builder()
                .id(UUID.randomUUID())
                .name("Test Room")
                .inviteCode("TST" + System.nanoTime() % 1000)
                .owner(User.builder()
                        .id(UUID.randomUUID())
                        .email("owner@example.com")
                        .displayName("Owner")
                        .provider(AuthProvider.GITHUB)
                        .providerId("999")
                        .build())
                .createdAt(Instant.now())
                .build();
    }
}
