package com.example.whiteboard.model;

/**
 * Type of drawing event on the canvas.
 * See ADR-2 in LU1_ARCHITECTURE.md for why the payload
 * is stored as JSONB rather than typed columns.
 */
public enum DrawingEventType {
    STROKE,
    SHAPE
}

