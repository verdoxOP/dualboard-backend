package com.example.whiteboard.security;

import com.example.whiteboard.model.AuthProvider;
import com.example.whiteboard.model.User;
import com.example.whiteboard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared helper that bridges an OAuth2/OIDC identity to our
 * PostgreSQL User entity.  Used by both CustomOAuth2UserService
 * (GitHub – plain OAuth 2.0) and CustomOidcUserService
 * (Google – OpenID Connect).
 *
 * Extracting this avoids duplicating the user-upsert logic in two services.
 */
@Component
@RequiredArgsConstructor
public class OAuthUserHelper {

    private final UserRepository userRepository;

    /**
     * Find-or-create the application User for the given OAuth attributes,
     * and return an enriched attribute map that includes our internal user ID.
     */
    public Map<String, Object> processOAuthUser(OAuth2User oauth2User, AuthProvider provider) {

        String providerId = extractProviderId(oauth2User, provider);
        String email      = extractEmail(oauth2User, provider);
        String displayName = extractDisplayName(oauth2User, provider);
        String avatarUrl   = extractAvatarUrl(oauth2User, provider);

        // Upsert: find existing user or create a new one (US-1.1)
        User user = userRepository.findByProviderAndProviderId(provider, providerId)
                .map(existing -> {
                    existing.setDisplayName(displayName);
                    existing.setAvatarUrl(avatarUrl);
                    existing.setEmail(email);
                    return userRepository.save(existing);
                })
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .email(email)
                            .displayName(displayName)
                            .avatarUrl(avatarUrl)
                            .provider(provider)
                            .providerId(providerId)
                            .build();
                    return userRepository.save(newUser);
                });

        // Enrich attributes so SecurityService.getCurrentUserId() works
        Map<String, Object> attributes = new HashMap<>(oauth2User.getAttributes());
        attributes.put("app_user_id", user.getId());
        attributes.put("app_display_name", user.getDisplayName());
        return attributes;
    }

    // ---- provider-specific extraction helpers ----

    private String extractProviderId(OAuth2User user, AuthProvider provider) {
        return switch (provider) {
            case GITHUB -> String.valueOf(user.getAttributes().get("id"));
            case GOOGLE -> (String) user.getAttributes().get("sub");
        };
    }

    private String extractEmail(OAuth2User user, AuthProvider provider) {
        String email = (String) user.getAttributes().get("email");
        if (email != null) return email;
        return switch (provider) {
            case GITHUB -> user.getAttributes().get("login") + "@users.noreply.github.com";
            case GOOGLE -> user.getAttributes().get("sub") + "@users.noreply.google.com";
        };
    }

    private String extractDisplayName(OAuth2User user, AuthProvider provider) {
        return switch (provider) {
            case GITHUB -> {
                String name = (String) user.getAttributes().get("name");
                yield name != null ? name : (String) user.getAttributes().get("login");
            }
            case GOOGLE -> (String) user.getAttributes().get("name");
        };
    }

    private String extractAvatarUrl(OAuth2User user, AuthProvider provider) {
        return switch (provider) {
            case GITHUB -> (String) user.getAttributes().get("avatar_url");
            case GOOGLE -> (String) user.getAttributes().get("picture");
        };
    }
}


