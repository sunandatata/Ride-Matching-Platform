package com.rideshare.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PasswordHasher.
 * Tests BCrypt hashing, verification, and security properties.
 */
class PasswordHasherTest {

    private PasswordHasher passwordHasher;

    @BeforeEach
    void setUp() {
        passwordHasher = new PasswordHasher();
    }

    // HASHING TESTS

    @Test
    void should_hash_password_successfully() {
        // Arrange
        String rawPassword = "mySecurePassword123!";

        // Act
        String hashedPassword = passwordHasher.hash(rawPassword);

        // Assert
        assertNotNull(hashedPassword);
        assertFalse(hashedPassword.isEmpty());
        assertNotEquals(rawPassword, hashedPassword);
        assertTrue(hashedPassword.startsWith("$2a$") || hashedPassword.startsWith("$2b$"));
    }

    @Test
    void should_throw_IllegalArgumentException_when_hashing_null_password() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> passwordHasher.hash(null));
    }

    @Test
    void should_throw_IllegalArgumentException_when_hashing_empty_password() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> passwordHasher.hash(""));
    }

    @Test
    void should_produce_different_hashes_for_same_password() {
        // Arrange
        String password = "myPassword123";

        // Act
        String hash1 = passwordHasher.hash(password);
        String hash2 = passwordHasher.hash(password);

        // Assert
        assertNotEquals(hash1, hash2);
    }

    // VERIFICATION TESTS

    @Test
    void should_verify_correct_password() {
        // Arrange
        String rawPassword = "correctPassword123";
        String hashedPassword = passwordHasher.hash(rawPassword);

        // Act
        boolean matches = passwordHasher.verify(rawPassword, hashedPassword);

        // Assert
        assertTrue(matches);
    }

    @Test
    void should_fail_verification_for_incorrect_password() {
        // Arrange
        String correctPassword = "correctPassword123";
        String wrongPassword = "wrongPassword456";
        String hashedPassword = passwordHasher.hash(correctPassword);

        // Act
        boolean matches = passwordHasher.verify(wrongPassword, hashedPassword);

        // Assert
        assertFalse(matches);
    }

    @Test
    void should_return_false_when_verifying_null_raw_password() {
        // Arrange
        String hashedPassword = passwordHasher.hash("somePassword");

        // Act
        boolean matches = passwordHasher.verify(null, hashedPassword);

        // Assert
        assertFalse(matches);
    }

    @Test
    void should_return_false_when_verifying_empty_raw_password() {
        // Arrange
        String hashedPassword = passwordHasher.hash("somePassword");

        // Act
        boolean matches = passwordHasher.verify("", hashedPassword);

        // Assert
        assertFalse(matches);
    }

    @Test
    void should_return_false_when_hash_is_null() {
        // Act
        boolean matches = passwordHasher.verify("password", null);

        // Assert
        assertFalse(matches);
    }

    @Test
    void should_handle_special_characters_in_password() {
        // Arrange
        String passwordWithSpecialChars = "P@ssw0rd!#$%^&*()_+-=[]{}|;':\"<>?,./";
        String hashedPassword = passwordHasher.hash(passwordWithSpecialChars);

        // Act
        boolean matches = passwordHasher.verify(passwordWithSpecialChars, hashedPassword);

        // Assert
        assertTrue(matches);
    }

    @Test
    void should_handle_unicode_characters_in_password() {
        // Arrange
        String passwordWithUnicode = "पासवर्ड123مرحبا";
        String hashedPassword = passwordHasher.hash(passwordWithUnicode);

        // Act
        boolean matches = passwordHasher.verify(passwordWithUnicode, hashedPassword);

        // Assert
        assertTrue(matches);
    }

    @Test
    void should_be_case_sensitive() {
        // Arrange
        String password = "MyPassword123";
        String differentCase = "mypassword123";
        String hashedPassword = passwordHasher.hash(password);

        // Act
        boolean matches = passwordHasher.verify(differentCase, hashedPassword);

        // Assert
        assertFalse(matches);
    }

    @Test
    void should_be_whitespace_sensitive() {
        // Arrange
        String password = "MyPassword123";
        String withSpace = "MyPassword123 ";
        String hashedPassword = passwordHasher.hash(password);

        // Act
        boolean matches = passwordHasher.verify(withSpace, hashedPassword);

        // Assert
        assertFalse(matches);
    }

    @Test
    void should_handle_very_long_password() {
        // Arrange
        String longPassword = "a".repeat(1000);
        String hashedPassword = passwordHasher.hash(longPassword);

        // Act
        boolean matches = passwordHasher.verify(longPassword, hashedPassword);

        // Assert
        assertTrue(matches);
    }
}
