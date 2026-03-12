package com.example.whiteboard.repository;

import com.example.whiteboard.model.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {

    /**
     * Find a room by its human-shareable invite code.
     * Used for the "Join Room" flow (US-2.2).
     */
    Optional<Room> findByInviteCode(String inviteCode);
}

