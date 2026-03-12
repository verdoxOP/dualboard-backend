package com.example.whiteboard.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a whiteboard room that users can create and join.
 *
 * Design notes (see LU1_ARCHITECTURE.md):
 * - inviteCode is a short, human-shareable 6-char code for joining
 * - UUID PK prevents enumeration attacks (ADR-1)
 * - Members tracked via RoomMembership entity (ADR-5)
 */
@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true, length = 6)
    private String inviteCode;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // The user who created this room
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    // All memberships in this room (ADR-5)
    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RoomMembership> memberships = new ArrayList<>();

    // All drawing events in this room
    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DrawingEvent> drawingEvents = new ArrayList<>();
}

