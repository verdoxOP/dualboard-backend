package com.example.whiteboard.security;

import com.example.whiteboard.model.AuthProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;

/**
 * Custom OIDC user service for providers that use OpenID Connect (Google).
 *
 * Why this exists (LU1 justification):
 *   GitHub uses plain OAuth 2.0  → handled by {@link CustomOAuth2UserService}
 *   Google uses OIDC on top of OAuth 2.0 → handled by THIS service
 *
 * Spring dispatches to the correct service automatically based on
 * the provider's registration metadata. Without this class, Google
 * logins would bypass our User entity mapping entirely and cause a 500.
 */
@Service
@RequiredArgsConstructor
public class CustomOidcUserService extends OidcUserService {

    private final OAuthUserHelper oauthUserHelper;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        // Let Spring's default OIDC service fetch the user info + ID token claims
        OidcUser oidcUser = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        AuthProvider provider = AuthProvider.valueOf(registrationId.toUpperCase());

        // Reuse the shared helper to upsert the User entity and enrich attributes
        Map<String, Object> enrichedAttributes = oauthUserHelper.processOAuthUser(oidcUser, provider);

        // Return a custom OidcUser that carries our enriched attributes
        // while preserving the original ID token + UserInfo for OIDC compliance
        return new EnrichedOidcUser(oidcUser, enrichedAttributes);
    }

    /**
     * Wraps the original OidcUser but overrides getAttributes() to include
     * our app_user_id and app_display_name.
     */
    private record EnrichedOidcUser(OidcUser delegate, Map<String, Object> enrichedAttributes) implements OidcUser {
        @Override public Map<String, Object> getClaims()           { return delegate.getClaims(); }
        @Override public OidcUserInfo getUserInfo()                 { return delegate.getUserInfo(); }
        @Override public OidcIdToken getIdToken()                   { return delegate.getIdToken(); }
        @Override public Map<String, Object> getAttributes()        { return enrichedAttributes; }
        @Override public Collection<? extends GrantedAuthority> getAuthorities() { return delegate.getAuthorities(); }
        @Override public String getName()                           { return delegate.getName(); }
    }
}



