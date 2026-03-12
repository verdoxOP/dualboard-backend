package com.example.whiteboard.repository;

import com.example.whiteboard.model.RoomMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomMembershipRepository extends JpaRepository<RoomMembership, Long> {

    /**
     * Find all memberships for a user — used for the room list dashboard (US-2.3).
     */
    List<RoomMembership> findByUserId(UUID userId);

    /**
     * Check if a specific user is a member of a specific room.
     * Used by SecurityService for @PreAuthorize checks (ADR-7).
     */
    Optional<RoomMembership> findByUserIdAndRoomId(UUID userId, UUID roomId);

    /**
     * Check existence without loading the entity — more efficient for auth checks.
     */
    boolean existsByUserIdAndRoomId(UUID userId, UUID roomId);
}

