package com.example.whiteboard.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A single drawing action (stroke or shape) on a room's canvas.
 *
 * Design notes (see LU1_ARCHITECTURE.md):
 * - Long PK: internal-only, sequential for efficient ordering (ADR-1 rationale)
 * - JSONB for data: flexible payload for different event types (ADR-2)
 * - Server-assigned timestamp: prevents client clock-skew issues (ADR-3)
 */
@Entity
@Table(name = "drawing_events", indexes = {
        @Index(name = "idx_drawing_events_room_id", columnList = "room_id"),
        @Index(name = "idx_drawing_events_timestamp", columnList = "timestamp")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DrawingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    // Store user ID directly rather than a full relationship,
    // since we don't need to navigate from event → user in queries.
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DrawingEventType type;

    /**
     * The drawing payload stored as PostgreSQL JSONB.
     * For STROKE: { "color": "#FF5733", "brushSize": 5, "points": [...] }
     * For SHAPE:  { "shapeType": "RECTANGLE", "x": 100, "y": 150, ... }
     *
     * Hibernate maps this to JSONB via @JdbcTypeCode(SqlTypes.JSON).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> data;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant timestamp;
}

