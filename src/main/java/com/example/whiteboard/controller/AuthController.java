package com.example.whiteboard.controller;

import com.example.whiteboard.dto.UserResponse;
import com.example.whiteboard.model.User;
import com.example.whiteboard.repository.UserRepository;
import com.example.whiteboard.security.SecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Authentication controller (US-1.1, US-1.2).
 *
 * Note: The actual OAuth2 login flow is handled by Spring Security
 * (GET /oauth2/authorization/{provider}). This controller only
 * provides the /me endpoint for the frontend to fetch the current user.
 *
 * Logout is configured in SecurityConfig as a Spring Security logout handler.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SecurityService securityService;
    private final UserRepository userRepository;

    /**
     * GET /api/v1/auth/me
     *
     * Returns the currently authenticated user's profile.
     * The frontend calls this after OAuth redirect to confirm login
     * and fetch user data for the dashboard.
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser() {
        UUID userId = securityService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        return userRepository.findById(userId)
                .map(user -> ResponseEntity.ok(toResponse(user)))
                .orElse(ResponseEntity.notFound().build());
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .avatarUrl(user.getAvatarUrl())
                .provider(user.getProvider().name())
                .build();
    }
}

