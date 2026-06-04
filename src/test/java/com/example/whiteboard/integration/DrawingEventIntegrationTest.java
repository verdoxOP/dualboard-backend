package com.example.whiteboard.integration;

import com.example.whiteboard.dto.CreateRoomRequest;
import com.example.whiteboard.dto.RoomResponse;
import com.example.whiteboard.model.AuthProvider;
import com.example.whiteboard.model.DrawingEvent;
import com.example.whiteboard.model.DrawingEventType;
import com.example.whiteboard.model.User;
import com.example.whiteboard.repository.DrawingEventRepository;
import com.example.whiteboard.repository.RoomMembershipRepository;
import com.example.whiteboard.repository.RoomRepository;
import com.example.whiteboard.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Drawing Event history functionality.
 * Tests room history retrieval with persisted drawing events.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:testdb"
})
class DrawingEventIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    
    @Autowired private UserRepository userRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMembershipRepository membershipRepository;
    @Autowired private DrawingEventRepository drawingEventRepository;

    private UUID userId;
    private User testUser;
    private UUID roomId;

    @BeforeEach
    void setUp() {
        // Clear all data
        drawingEventRepository.deleteAll();
        membershipRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();

        // Create test user
        userId = UUID.randomUUID();
        testUser = User.builder()
                .id(userId)
                .email("drawer@example.com")
                .displayName("Artist")
                .provider(AuthProvider.GITHUB)
                .providerId("artist-123")
                .build();
        testUser = userRepository.save(testUser);

        // Create test room (we'll do this manually in the tests or via the API)
    }

    // ---- Room History Retrieval ----

    @Test
    @WithMockUser
    void getRoomHistory_emptyRoom_returnsEmptyList() throws Exception {
        // Create a room
        CreateRoomRequest request = new CreateRoomRequest("Empty Room");
        var response = mockMvc.perform(post("/api/v1/rooms")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        RoomResponse room = objectMapper.readValue(response, RoomResponse.class);
        roomId = room.id();

        // Get history
        mockMvc.perform(get("/api/v1/rooms/" + roomId + "/history")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser
    void getRoomHistory_withEvents_returnsInChronologicalOrder() throws Exception {
        // Create room manually
        var room = com.example.whiteboard.model.Room.builder()
                .id(UUID.randomUUID())
                .name("History Room")
                .inviteCode("HIS001")
                .owner(testUser)
                .build();
        room = roomRepository.save(room);
        roomId = room.getId();

        // Create memberships
        var membership = com.example.whiteboard.model.RoomMembership.builder()
                .user(testUser)
                .room(room)
                .role(com.example.whiteboard.model.RoomRole.OWNER)
                .build();
        membershipRepository.save(membership);

        // Add drawing events
        var event1 = DrawingEvent.builder()
                .room(room)
                .userId(userId)
                .type(DrawingEventType.STROKE)
                .data(Map.of("color", "#FF0000", "x", 10))
                .build();
        drawingEventRepository.save(event1);

        // Small delay to ensure different timestamps
        Thread.sleep(100);

        var event2 = DrawingEvent.builder()
                .room(room)
                .userId(userId)
                .type(DrawingEventType.SHAPE)
                .data(Map.of("type", "RECTANGLE", "x", 20))
                .build();
        drawingEventRepository.save(event2);

        // Get history
        mockMvc.perform(get("/api/v1/rooms/" + roomId + "/history")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("STROKE"))
                .andExpect(jsonPath("$[1].type").value("SHAPE"));
    }

    @Test
    @WithMockUser
    void getRoomHistory_accessControl_nonMemberForbidden() throws Exception {
        // Create room with different owner
        UUID ownerId = UUID.randomUUID();
        User owner = User.builder()
                .id(ownerId)
                .email("owner@example.com")
                .displayName("Owner")
                .provider(AuthProvider.GITHUB)
                .providerId("owner-123")
                .build();
        owner = userRepository.save(owner);

        var room = com.example.whiteboard.model.Room.builder()
                .id(UUID.randomUUID())
                .name("Private Room")
                .inviteCode("PRI001")
                .owner(owner)
                .build();
        room = roomRepository.save(room);

        // Try to access history as non-member
        mockMvc.perform(get("/api/v1/rooms/" + room.getId() + "/history")
                .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void getRoomHistory_multipleSenders_preservesUserInfo() throws Exception {
        // Create room
        var room = com.example.whiteboard.model.Room.builder()
                .id(UUID.randomUUID())
                .name("Collab Room")
                .inviteCode("COL001")
                .owner(testUser)
                .build();
        room = roomRepository.save(room);
        roomId = room.getId();

        // Create membership
        var membership = com.example.whiteboard.model.RoomMembership.builder()
                .user(testUser)
                .room(room)
                .role(com.example.whiteboard.model.RoomRole.OWNER)
                .build();
        membershipRepository.save(membership);

        // Create another user
        UUID user2Id = UUID.randomUUID();
        User user2 = User.builder()
                .id(user2Id)
                .email("user2@example.com")
                .displayName("Collaborator")
                .provider(AuthProvider.GITHUB)
                .providerId("collab-123")
                .build();
        user2 = userRepository.save(user2);

        // Add membership for second user
        var membership2 = com.example.whiteboard.model.RoomMembership.builder()
                .user(user2)
                .room(room)
                .role(com.example.whiteboard.model.RoomRole.MEMBER)
                .build();
        membershipRepository.save(membership2);

        // Add events from both users
        var event1 = DrawingEvent.builder()
                .room(room)
                .userId(userId)
                .type(DrawingEventType.STROKE)
                .data(Map.of("color", "#0000FF"))
                .build();
        drawingEventRepository.save(event1);

        var event2 = DrawingEvent.builder()
                .room(room)
                .userId(user2Id)
                .type(DrawingEventType.STROKE)
                .data(Map.of("color", "#00FF00"))
                .build();
        drawingEventRepository.save(event2);

        // Get history
        mockMvc.perform(get("/api/v1/rooms/" + roomId + "/history")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].senderName").value("Artist"))
                .andExpect(jsonPath("$[1].senderName").value("Collaborator"));
    }

    @Test
    @WithMockUser
    void getRoomHistory_unknownUserId_setsDefaultName() throws Exception {
        // Create room
        var room = com.example.whiteboard.model.Room.builder()
                .id(UUID.randomUUID())
                .name("Test Room")
                .inviteCode("TEST001")
                .owner(testUser)
                .build();
        room = roomRepository.save(room);
        roomId = room.getId();

        // Create membership
        var membership = com.example.whiteboard.model.RoomMembership.builder()
                .user(testUser)
                .room(room)
                .role(com.example.whiteboard.model.RoomRole.OWNER)
                .build();
        membershipRepository.save(membership);

        // Add event with non-existent user ID
        UUID unknownId = UUID.randomUUID();
        var event = DrawingEvent.builder()
                .room(room)
                .userId(unknownId)
                .type(DrawingEventType.STROKE)
                .data(Map.of("x", 1))
                .build();
        drawingEventRepository.save(event);

        // Get history
        mockMvc.perform(get("/api/v1/rooms/" + roomId + "/history")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].senderName").value("Unknown"));
    }
}
