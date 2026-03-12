package com.example.whiteboard.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Links a User to a Room with a specific role.
 *
 * This is a full entity instead of a plain @ManyToMany join table (ADR-5).
 * Rationale: We need role (OWNER/MEMBER) and joinedAt metadata on the
 * relationship. A plain join table cannot hold extra columns without
 * a full refactor later.
 *
 * Unique constraint on (user, room) prevents duplicate memberships.
 */
@Entity
@Table(name = "room_memberships", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "room_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomRole role;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant joinedAt;
}

