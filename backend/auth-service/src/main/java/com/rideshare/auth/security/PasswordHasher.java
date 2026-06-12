package com.rideshare.auth.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Password hashing and verification component.
 * Uses BCrypt for secure password storage with adaptive work factor.
 * Designed to resist brute-force attacks through computational cost.
 */
@Slf4j
@Component
public class PasswordHasher {

    private final BCryptPasswordEncoder encoder;

    public PasswordHasher() {
        // Strength of 12 provides good balance between security and performance
        // ~200ms per hash at current hardware speeds
        this.encoder = new BCryptPasswordEncoder(12);
        log.info("Password Hasher initialized with BCrypt strength factor 12");
    }

    /**
     * Hash a plain text password.
     * Should be called during user registration and password changes.
     *
     * @param rawPassword The plain text password
     * @return Hashed password safe for storage
     */
    public String hash(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        return encoder.encode(rawPassword);
    }

    /**
     * Verify if a plain text password matches a hash.
     * Used during login authentication.
     *
     * @param rawPassword The plain text password from login
     * @param hashedPassword The stored hash from database
     * @return True if password matches hash, false otherwise
     */
    public boolean verify(String rawPassword, String hashedPassword) {
        if (rawPassword == null || rawPassword.isEmpty() || hashedPassword == null) {
            return false;
        }
        try {
            return encoder.matches(rawPassword, hashedPassword);
        } catch (IllegalArgumentException e) {
            log.warn("Password verification failed: {}", e.getMessage());
            return false;
        }
    }
}
