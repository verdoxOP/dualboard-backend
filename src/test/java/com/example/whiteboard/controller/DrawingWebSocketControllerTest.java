package com.example.whiteboard.controller;

import com.example.whiteboard.dto.DrawingErrorResponse;
import com.example.whiteboard.dto.DrawingEventRequest;
import com.example.whiteboard.service.DrawingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for DrawingWebSocketController.
 *
 * The controller is intentionally thin — it delegates straight to DrawingService.
 * These tests verify:
 *  - handleDraw delegates to DrawingService with correct arguments
 *  - exceptions from the service propagate so @MessageExceptionHandler can intercept them
 *  - handleAccessDenied returns a FORBIDDEN DrawingErrorResponse
 *  - handleValidationError returns a BAD_REQUEST DrawingErrorResponse
 */
@ExtendWith(MockitoExtension.class)
class DrawingWebSocketControllerTest {

    @Mock private DrawingService drawingService;

    @InjectMocks private DrawingWebSocketController controller;

    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final DrawingEventRequest REQUEST = new DrawingEventRequest(
            "STROKE", Map.of("color", "#FF0000", "brushSize", 5));

    @Test
    void handleDraw_delegatesToDrawingService() {
        controller.handleDraw(ROOM_ID, REQUEST);
        verify(drawingService).processDrawEvent(ROOM_ID, REQUEST);
    }

    @Test
    void handleDraw_whenServiceThrowsAccessDenied_exceptionPropagatesForHandlerToIntercept() {
        doThrow(new AccessDeniedException("Not a member"))
                .when(drawingService).processDrawEvent(ROOM_ID, REQUEST);

        // The exception must propagate so @MessageExceptionHandler can catch it
        assertThatThrownBy(() -> controller.handleDraw(ROOM_ID, REQUEST))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Not a member");
    }

    @Test
    void handleDraw_whenServiceThrowsIllegalArgument_exceptionPropagatesForHandlerToIntercept() {
        doThrow(new IllegalArgumentException("Unknown event type: CIRCLE"))
                .when(drawingService).processDrawEvent(ROOM_ID, REQUEST);

        assertThatThrownBy(() -> controller.handleDraw(ROOM_ID, REQUEST))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown event type: CIRCLE");
    }

    @Test
    void handleAccessDenied_returnsForbiddenErrorResponse() {
        AccessDeniedException ex = new AccessDeniedException("Not a member of room abc");
        DrawingErrorResponse response = controller.handleAccessDenied(ex);
        assertThat(response.code()).isEqualTo("FORBIDDEN");
        assertThat(response.message()).isEqualTo("Not a member of room abc");
    }

    @Test
    void handleValidationError_returnsBadRequestErrorResponse() {
        IllegalArgumentException ex = new IllegalArgumentException("Unknown event type: CIRCLE");
        DrawingErrorResponse response = controller.handleValidationError(ex);
        assertThat(response.code()).isEqualTo("BAD_REQUEST");
        assertThat(response.message()).isEqualTo("Unknown event type: CIRCLE");
    }
}
