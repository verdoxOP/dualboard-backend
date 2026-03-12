package com.example.whiteboard.security;

import com.example.whiteboard.model.AuthProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Custom OAuth2 user service for providers that use plain OAuth 2.0 (GitHub).
 *
 * Google uses OIDC and is handled by {@link CustomOidcUserService}.
 * Both services share user-upsert logic via {@link OAuthUserHelper}.
 */
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final OAuthUserHelper oauthUserHelper;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        AuthProvider provider = AuthProvider.valueOf(registrationId.toUpperCase());

        // Delegate to shared helper for user upsert + attribute enrichment
        Map<String, Object> enrichedAttributes = oauthUserHelper.processOAuthUser(oauth2User, provider);

        return new DefaultOAuth2User(
                oauth2User.getAuthorities(),
                enrichedAttributes,
                userRequest.getClientRegistration().getProviderDetails()
                        .getUserInfoEndpoint().getUserNameAttributeName()
        );
    }
}

