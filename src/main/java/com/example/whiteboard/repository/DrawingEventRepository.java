package com.example.whiteboard.repository;

import com.example.whiteboard.model.DrawingEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DrawingEventRepository extends JpaRepository<DrawingEvent, Long> {

    /**
     * Fetch all drawing events for a room, ordered by timestamp.
     * This is the "Full State on Join" query (ADR-6).
     * Returns events in chronological order so the frontend can replay them.
     */
    List<DrawingEvent> findByRoomIdOrderByTimestampAsc(UUID roomId);
}

