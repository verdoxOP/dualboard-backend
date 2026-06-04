package com.example.whiteboard.security;

import com.example.whiteboard.repository.RoomMembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SecurityService.
 * Tests membership checks and user ID extraction from security context.
 */
@ExtendWith(MockitoExtension.class)
class SecurityServiceTest {

    @Mock private RoomMembershipRepository membershipRepository;
    @Mock private SecurityContext securityContext;
    @Mock private Authentication authentication;
    @Mock private OAuth2User oauth2User;

    @InjectMocks private SecurityService securityService;

    private UUID testUserId;
    private UUID testRoomId;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testRoomId = UUID.randomUUID();
    }

    // ---- getCurrentUserId() from SecurityContext ----

    @Test
    void getCurrentUserId_validUUIDAttribute_returnsUUID() {
        setupSecurityContext(authentication, oauth2User);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("app_user_id", testUserId);
        when(oauth2User.getAttributes()).thenReturn(attributes);

        UUID result = securityService.getCurrentUserId();

        assertThat(result).isEqualTo(testUserId);
    }

    @Test
    void getCurrentUserId_stringUUIDAttribute_returnsUUID() {
        setupSecurityContext(authentication, oauth2User);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("app_user_id", testUserId.toString());
        when(oauth2User.getAttributes()).thenReturn(attributes);

        UUID result = securityService.getCurrentUserId();

        assertThat(result).isEqualTo(testUserId);
    }

    @Test
    void getCurrentUserId_noAuthentication_returnsNull() {
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(null);

        UUID result = securityService.getCurrentUserId();

        assertThat(result).isNull();
    }

    @Test
    void getCurrentUserId_notAuthenticated_returnsNull() {
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(false);

        UUID result = securityService.getCurrentUserId();

        assertThat(result).isNull();
    }

    @Test
    void getCurrentUserId_principalNotOAuth2_returnsNull() {
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("string-principal");

        UUID result = securityService.getCurrentUserId();

        assertThat(result).isNull();
    }

    @Test
    void getCurrentUserId_noAppUserIdAttribute_returnsNull() {
        setupSecurityContext(authentication, oauth2User);
        when(oauth2User.getAttributes()).thenReturn(new HashMap<>());

        UUID result = securityService.getCurrentUserId();

        assertThat(result).isNull();
    }

    // ---- getCurrentUserId(Principal) ----

    @Test
    void getCurrentUserId_withPrincipal_validUUID_returnsUUID() {
        OAuth2User oauth2User = mock(OAuth2User.class);
        Authentication authentication = mock(Authentication.class);
        
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("app_user_id", testUserId);
        when(oauth2User.getAttributes()).thenReturn(attributes);
        when(authentication.getPrincipal()).thenReturn(oauth2User);
        
        // Cast authentication to Principal
        UUID result = securityService.getCurrentUserId((java.security.Principal) authentication);

        assertThat(result).isEqualTo(testUserId);
    }

    @Test
    void getCurrentUserId_withPrincipal_nullPrincipal_fallsBackToContext() {
        setupSecurityContext(authentication, oauth2User);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("app_user_id", testUserId);
        when(oauth2User.getAttributes()).thenReturn(attributes);

        UUID result = securityService.getCurrentUserId((java.security.Principal) null);

        assertThat(result).isEqualTo(testUserId);
    }

    @Test
    void getCurrentUserId_withPrincipal_authentication_extractsUserId() {
        Authentication authPrincipal = mock(Authentication.class);
        when(authPrincipal.getPrincipal()).thenReturn(oauth2User);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("app_user_id", testUserId);
        when(oauth2User.getAttributes()).thenReturn(attributes);

        UUID result = securityService.getCurrentUserId((java.security.Principal) authPrincipal);

        assertThat(result).isEqualTo(testUserId);
    }

    // ---- isMember(UUID) ----

    @Test
    void isMember_userIsMember_returnsTrue() {
        setupSecurityContext(authentication, oauth2User);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("app_user_id", testUserId);
        when(oauth2User.getAttributes()).thenReturn(attributes);
        when(membershipRepository.existsByUserIdAndRoomId(testUserId, testRoomId)).thenReturn(true);

        boolean result = securityService.isMember(testRoomId);

        assertThat(result).isTrue();
        verify(membershipRepository).existsByUserIdAndRoomId(testUserId, testRoomId);
    }

    @Test
    void isMember_userNotMember_returnsFalse() {
        setupSecurityContext(authentication, oauth2User);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("app_user_id", testUserId);
        when(oauth2User.getAttributes()).thenReturn(attributes);
        when(membershipRepository.existsByUserIdAndRoomId(testUserId, testRoomId)).thenReturn(false);

        boolean result = securityService.isMember(testRoomId);

        assertThat(result).isFalse();
    }

    @Test
    void isMember_notAuthenticated_returnsFalse() {
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(null);

        boolean result = securityService.isMember(testRoomId);

        assertThat(result).isFalse();
        verifyNoInteractions(membershipRepository);
    }

    @Test
    void isMember_noUserIdInContext_returnsFalse() {
        setupSecurityContext(authentication, oauth2User);
        when(oauth2User.getAttributes()).thenReturn(new HashMap<>());

        boolean result = securityService.isMember(testRoomId);

        assertThat(result).isFalse();
        verifyNoInteractions(membershipRepository);
    }

    // ---- Helper ----

    private void setupSecurityContext(Authentication auth, OAuth2User user) {
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(auth);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(user);
    }
}
