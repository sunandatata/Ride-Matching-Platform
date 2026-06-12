package com.rideshare.auth.security;

import com.rideshare.auth.entity.User;
import com.rideshare.auth.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtTokenProvider.
 * Tests token generation, validation, and claim extraction.
 * Covers token expiration, signature validation, and malformed token handling.
 */
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private User testUser;

    @BeforeEach
    void setUp() {
        // Initialize with test secret key
        jwtTokenProvider = new JwtTokenProvider(
            "my-secret-key-for-jwt-testing-must-be-long-enough",
            1,  // 1 hour access token
            7,  // 7 days refresh token
            "rideshare.local",
            "rideshare-api"
        );

        testUser = User.builder()
            .userId("user-123")
            .phoneNumber("+1234567890")
            .firstName("John")
            .lastName("Doe")
            .userType(User.UserType.RIDER)
            .build();
    }

    // TOKEN GENERATION TESTS

    @Test
    void should_generate_valid_access_token() {
        // Act
        String token = jwtTokenProvider.generateAccessToken(testUser);

        // Assert
        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(token.contains("."));

        // Validate the token
        Claims claims = jwtTokenProvider.validateAndGetClaims(token);
        assertEquals("user-123", claims.getSubject());
        assertEquals("RIDER", claims.get("type"));
    }

    @Test
    void should_generate_valid_refresh_token() {
        // Act
        String token = jwtTokenProvider.generateRefreshToken("user-123", "device-456");

        // Assert
        assertNotNull(token);
        assertFalse(token.isEmpty());

        Claims claims = jwtTokenProvider.validateAndGetClaims(token);
        assertEquals("user-123", claims.getSubject());
        assertEquals("device-456", claims.get("device_id"));
        assertEquals("refresh", claims.get("type"));
    }

    @Test
    void should_include_correct_issuer_in_token() {
        // Act
        String token = jwtTokenProvider.generateAccessToken(testUser);

        // Assert
        Claims claims = jwtTokenProvider.validateAndGetClaims(token);
        assertEquals("rideshare.local", claims.getIssuer());
    }

    @Test
    void should_include_correct_audience_in_token() {
        // Act
        String token = jwtTokenProvider.generateAccessToken(testUser);

        // Assert
        Claims claims = jwtTokenProvider.validateAndGetClaims(token);
        assertTrue(claims.getAudience().contains("rideshare-api"));
    }

    @Test
    void should_include_jwt_id_in_token() {
        // Act
        String token = jwtTokenProvider.generateAccessToken(testUser);

        // Assert
        Claims claims = jwtTokenProvider.validateAndGetClaims(token);
        assertNotNull(claims.getId());
        assertFalse(claims.getId().isEmpty());
    }

    @Test
    void should_set_correct_scopes_for_rider() {
        // Act
        String token = jwtTokenProvider.generateAccessToken("user-123", "RIDER", false);

        // Assert
        Claims claims = jwtTokenProvider.validateAndGetClaims(token);
        String scope = (String) claims.get("scope");
        assertTrue(scope.contains("rides:read"));
        assertTrue(scope.contains("rides:write"));
        assertTrue(scope.contains("profile:read"));
    }

    @Test
    void should_set_correct_scopes_for_driver() {
        // Act
        String token = jwtTokenProvider.generateAccessToken("user-123", "DRIVER", false);

        // Assert
        Claims claims = jwtTokenProvider.validateAndGetClaims(token);
        String scope = (String) claims.get("scope");
        assertTrue(scope.contains("earnings:read"));
        assertTrue(scope.contains("locations:write"));
    }

    @Test
    void should_set_correct_scopes_for_admin() {
        // Act
        String token = jwtTokenProvider.generateAccessToken("user-123", "RIDER", true);

        // Assert
        Claims claims = jwtTokenProvider.validateAndGetClaims(token);
        String scope = (String) claims.get("scope");
        assertTrue(scope.contains("admin:read"));
        assertTrue(scope.contains("admin:write"));
    }

    // TOKEN VALIDATION TESTS

    @Test
    void should_validate_valid_token() {
        // Arrange
        String token = jwtTokenProvider.generateAccessToken(testUser);

        // Act & Assert
        assertDoesNotThrow(() -> jwtTokenProvider.validateAndGetClaims(token));
    }

    @Test
    void should_throw_InvalidTokenException_for_invalid_signature() {
        // Arrange
        String token = jwtTokenProvider.generateAccessToken(testUser);
        // Tamper with token signature
        String tamperedToken = token.substring(0, token.length() - 10) + "tampered!!";

        // Act & Assert
        assertThrows(InvalidTokenException.class, () ->
            jwtTokenProvider.validateAndGetClaims(tamperedToken));
    }

    @Test
    void should_throw_InvalidTokenException_for_malformed_token() {
        // Act & Assert
        assertThrows(InvalidTokenException.class, () ->
            jwtTokenProvider.validateAndGetClaims("malformed.token"));
    }

    @Test
    void should_throw_InvalidTokenException_for_empty_token() {
        // Act & Assert
        assertThrows(InvalidTokenException.class, () ->
            jwtTokenProvider.validateAndGetClaims(""));
    }

    @Test
    void should_throw_InvalidTokenException_for_null_token() {
        // Act & Assert
        assertThrows(InvalidTokenException.class, () ->
            jwtTokenProvider.validateAndGetClaims(null));
    }

    // CLAIM EXTRACTION TESTS

    @Test
    void should_extract_user_id_from_token() {
        // Arrange
        String token = jwtTokenProvider.generateAccessToken(testUser);

        // Act
        String userId = jwtTokenProvider.getUserIdFromToken(token);

        // Assert
        assertEquals("user-123", userId);
    }

    @Test
    void should_return_null_when_extracting_user_id_from_invalid_token() {
        // Act
        String userId = jwtTokenProvider.getUserIdFromToken("invalid.token");

        // Assert
        assertNull(userId);
    }

    @Test
    void should_extract_jti_from_token() {
        // Arrange
        String token = jwtTokenProvider.generateAccessToken(testUser);

        // Act
        String jti = jwtTokenProvider.getJtiFromToken(token);

        // Assert
        assertNotNull(jti);
        assertFalse(jti.isEmpty());
    }

    @Test
    void should_extract_expiration_time_from_token() {
        // Arrange
        String token = jwtTokenProvider.generateAccessToken(testUser);

        // Act
        var expirationTime = jwtTokenProvider.getExpirationTime(token);

        // Assert
        assertNotNull(expirationTime);
        assertTrue(expirationTime.isAfter(java.time.Instant.now()));
    }

    @Test
    void should_return_null_when_extracting_expiration_from_invalid_token() {
        // Act
        var expirationTime = jwtTokenProvider.getExpirationTime("invalid.token");

        // Assert
        assertNull(expirationTime);
    }

    // EXPIRY TIME TESTS

    @Test
    void should_return_correct_access_token_expiry_seconds() {
        // Act
        long expirySeconds = jwtTokenProvider.getAccessTokenExpirySeconds();

        // Assert
        assertEquals(3600, expirySeconds); // 1 hour
    }

    @Test
    void should_create_token_with_future_expiration() {
        // Arrange
        String token = jwtTokenProvider.generateAccessToken(testUser);

        // Act
        Claims claims = jwtTokenProvider.validateAndGetClaims(token);
        var expirationTime = claims.getExpiration().toInstant();

        // Assert
        assertTrue(expirationTime.isAfter(java.time.Instant.now()));
    }
}
