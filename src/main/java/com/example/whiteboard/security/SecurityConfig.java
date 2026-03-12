package com.example.whiteboard.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security configuration for DualBoard.
 *
 * Key decisions:
 * - @EnableMethodSecurity enables @PreAuthorize on controllers (ADR-7)
 * - OAuth2 login with custom user service that bridges to our User entity
 * - CORS configured for Next.js frontend on localhost:3000
 * - /api/** endpoints return 401 instead of redirecting to login page
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // Required for @PreAuthorize (ADR-7)
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomOidcUserService customOidcUserService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CORS: allow Next.js frontend
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // CSRF: disable for API endpoints (stateless JWT-style communication)
                .csrf(csrf -> csrf.disable())

                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/", "/error", "/login/**", "/oauth2/**").permitAll()
                        // H2 console (dev profile only)
                        .requestMatchers("/h2-console/**").permitAll()
                        // WebSocket handshake endpoint
                        .requestMatchers("/ws/**").permitAll()
                        // Everything under /api/ requires authentication
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated()
                )

                // Allow H2 console to load in iframes
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                )

                // For API calls: return 401 instead of redirect to login page
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )

                // OAuth2 login configuration
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)       // GitHub (plain OAuth 2.0)
                                .oidcUserService(customOidcUserService)     // Google (OpenID Connect)
                        )
                        // After successful login, redirect to the Next.js frontend
                        // For dev without frontend: use /api/v1/auth/me to verify login
                        .defaultSuccessUrl("http://localhost:3000/dashboard", true)
                        .failureUrl("/login?error=true")
                )

                // Logout configuration
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .logoutSuccessUrl("http://localhost:3000/")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);  // Required for session cookies

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

