package com.example.whiteboard.security;

import com.example.whiteboard.repository.RoomMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Custom security service for declarative authorization via @PreAuthorize.
 *
 * Usage in controllers:
 *   @PreAuthorize("@securityService.isMember(#roomId)")
 *
 * See ADR-7 in LU1_ARCHITECTURE.md for why this approach was chosen
 * over manual checks or JWT-encoded membership.
 */
@Service("securityService")
@RequiredArgsConstructor
public class SecurityService {

    private final RoomMembershipRepository membershipRepository;

    /**
     * Checks if the currently authenticated user is a member of the given room.
     * Called by Spring Security's SpEL expression engine.
     */
    public boolean isMember(UUID roomId) {
        UUID userId = getCurrentUserId();
        if (userId == null) {
            return false;
        }
        return membershipRepository.existsByUserIdAndRoomId(userId, roomId);
    }

    /**
     * Extracts the application-level User UUID from the current security context.
     * Returns null if not authenticated.
     *
     * Note: The actual UUID is stored as an attribute on the OAuth2User principal
     * by our CustomOAuth2UserService during login.
     */
    public UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }

        Object principal = auth.getPrincipal();
        if (principal instanceof OAuth2User oauth2User) {
            Object userId = oauth2User.getAttributes().get("app_user_id");
            if (userId instanceof UUID uuid) {
                return uuid;
            }
            if (userId instanceof String str) {
                return UUID.fromString(str);
            }
        }
        return null;
    }
}

