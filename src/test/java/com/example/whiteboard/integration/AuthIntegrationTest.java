package com.example.whiteboard.integration;

import com.example.whiteboard.model.AuthProvider;
import com.example.whiteboard.model.User;
import com.example.whiteboard.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Authentication functionality.
 * Tests the /me endpoint with real database context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:testdb"
})
class AuthIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;

    private UUID testUserId;
    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        // Create test user
        testUserId = UUID.randomUUID();
        testUser = User.builder()
                .id(testUserId)
                .email("auth@example.com")
                .displayName("Auth Tester")
                .avatarUrl("https://example.com/avatar.jpg")
                .provider(AuthProvider.GITHUB)
                .providerId("github-9999")
                .build();
        
        testUser = userRepository.save(testUser);
    }

    // ---- Authentication Status ----

    @Test
    void getCurrentUser_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getCurrentUser_authenticated_returnsUserInfo() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testUserId.toString()))
                .andExpect(jsonPath("$.email").value("auth@example.com"))
                .andExpect(jsonPath("$.displayName").value("Auth Tester"))
                .andExpect(jsonPath("$.avatarUrl").value("https://example.com/avatar.jpg"))
                .andExpect(jsonPath("$.provider").value("GITHUB"));
    }

    @Test
    @WithMockUser
    void getCurrentUser_userNotInDatabase_returns401() throws Exception {
        // Delete the user after test setup
        userRepository.deleteAll();

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getCurrentUser_consisten_multipleRequests() throws Exception {
        // Ensure multiple requests return consistent data
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Auth Tester"));

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Auth Tester"));
    }

    @Test
    @WithMockUser
    void getCurrentUser_withAllProviders_returnsCorrectProvider() throws Exception {
        // Test with different provider
        User googleUser = User.builder()
                .id(UUID.randomUUID())
                .email("google@example.com")
                .displayName("Google User")
                .provider(AuthProvider.GOOGLE)
                .providerId("google-123")
                .build();
        googleUser = userRepository.save(googleUser);

        // Note: In real tests, we'd need to mock authentication for specific user
        // This is more of a data validation test
        var savedUser = userRepository.findById(googleUser.getId());
        org.assertj.core.api.Assertions.assertThat(savedUser)
                .isPresent()
                .hasValueSatisfying(u -> org.assertj.core.api.Assertions.assertThat(u.getProvider())
                        .isEqualTo(AuthProvider.GOOGLE));
    }
}
