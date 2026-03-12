package com.example.whiteboard.model;

/**
 * Role within a room membership.
 * OWNER can delete the room; MEMBER can only draw.
 * See ADR-5 in LU1_ARCHITECTURE.md.
 */
public enum RoomRole {
    OWNER,
    MEMBER
}

