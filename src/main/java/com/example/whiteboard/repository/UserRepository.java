package com.example.whiteboard.repository;

import com.example.whiteboard.model.User;
import com.example.whiteboard.model.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find a user by their OAuth provider and provider-specific ID.
     * Used during OAuth login to check if the user already exists.
     */
    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);

    Optional<User> findByEmail(String email);
}

