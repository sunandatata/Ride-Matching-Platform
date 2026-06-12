package com.rideshare.auth.service;

import com.rideshare.auth.dto.request.LoginRequest;
import com.rideshare.auth.dto.request.RefreshTokenRequest;
import com.rideshare.auth.dto.response.LoginResponse;
import com.rideshare.auth.dto.response.RefreshTokenResponse;
import com.rideshare.auth.entity.RefreshToken;
import com.rideshare.auth.entity.TokenBlacklist;
import com.rideshare.auth.entity.User;
import com.rideshare.auth.exception.InvalidCredentialsException;
import com.rideshare.auth.exception.InvalidTokenException;
import com.rideshare.auth.exception.UserNotFoundException;
import com.rideshare.auth.repository.RefreshTokenRepository;
import com.rideshare.auth.repository.TokenBlacklistRepository;
import com.rideshare.auth.repository.UserRepository;
import com.rideshare.auth.security.JwtTokenProvider;
import com.rideshare.auth.security.PasswordHasher;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService.
 * Tests login, token refresh, logout, and token validation logic.
 * Covers both happy paths and error scenarios.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private TokenBlacklistRepository tokenBlacklistRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .userId("user-123")
            .phoneNumber("+1234567890")
            .passwordHash("$2a$12$hashedPassword")
            .firstName("John")
            .lastName("Doe")
            .email("john@example.com")
            .userType(User.UserType.RIDER)
            .status(User.UserStatus.ACTIVE)
            .mfaEnabled(false)
            .kycVerified(true)
            .build();

        loginRequest = LoginRequest.builder()
            .phoneNumber("+1234567890")
            .password("plainPassword")
            .deviceId("device-456")
            .deviceType("ios")
            .build();
    }

    // LOGIN TESTS

    @Test
    void should_login_successfully_when_credentials_are_valid() {
        // Arrange
        when(userRepository.findActiveByPhoneNumber("+1234567890")).thenReturn(Optional.of(testUser));
        when(passwordHasher.verify("plainPassword", "$2a$12$hashedPassword")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(testUser)).thenReturn("access-token-123");
        when(jwtTokenProvider.generateRefreshToken("user-123", "device-456")).thenReturn("refresh-token-123");
        when(jwtTokenProvider.getAccessTokenExpirySeconds()).thenReturn(3600L);

        // Act
        LoginResponse response = authService.login(loginRequest);

        // Assert
        assertNotNull(response);
        assertEquals("access-token-123", response.getAccessToken());
        assertEquals("refresh-token-123", response.getRefreshToken());
        assertEquals(3600L, response.getExpiresIn());
        assertEquals("Bearer", response.getTokenType());
        assertEquals("John", response.getUser().getFirstName());
        assertFalse(response.getMfaRequired());

        // Verify refresh token was stored
        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
    }

    @Test
    void should_throw_InvalidCredentialsException_when_user_not_found() {
        // Arrange
        when(userRepository.findActiveByPhoneNumber("+1234567890")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(InvalidCredentialsException.class, () -> authService.login(loginRequest));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void should_throw_InvalidCredentialsException_when_password_is_wrong() {
        // Arrange
        when(userRepository.findActiveByPhoneNumber("+1234567890")).thenReturn(Optional.of(testUser));
        when(passwordHasher.verify("plainPassword", "$2a$12$hashedPassword")).thenReturn(false);

        // Act & Assert
        assertThrows(InvalidCredentialsException.class, () -> authService.login(loginRequest));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void should_return_mfa_required_when_user_has_mfa_enabled() {
        // Arrange
        User userWithMfa = User.builder()
            .userId("user-456")
            .phoneNumber("+1234567890")
            .passwordHash("$2a$12$hashedPassword")
            .firstName("Jane")
            .lastName("Doe")
            .mfaEnabled(true)
            .mfaPhoneNumber("+1234567890")
            .userType(User.UserType.DRIVER)
            .status(User.UserStatus.ACTIVE)
            .build();

        when(userRepository.findActiveByPhoneNumber("+1234567890")).thenReturn(Optional.of(userWithMfa));
        when(passwordHasher.verify("plainPassword", "$2a$12$hashedPassword")).thenReturn(true);

        // Act
        LoginResponse response = authService.login(loginRequest);

        // Assert
        assertNotNull(response);
        assertTrue(response.getMfaRequired());
        assertNull(response.getAccessToken());
        assertNull(response.getRefreshToken());
        verify(refreshTokenRepository, never()).save(any());
    }

    // TOKEN REFRESH TESTS

    @Test
    void should_refresh_token_successfully_when_refresh_token_is_valid() {
        // Arrange
        String refreshToken = "valid-refresh-token";
        RefreshTokenRequest request = RefreshTokenRequest.builder()
            .refreshToken(refreshToken)
            .build();

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("user-123");
        when(claims.getId()).thenReturn("jti-123");

        when(jwtTokenProvider.validateAndGetClaims(refreshToken)).thenReturn(claims);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(
            RefreshToken.builder()
                .tokenId("token-123")
                .userId("user-123")
                .revoked(false)
                .expiresAt(Instant.now().plusSeconds(86400))
                .build()
        ));
        when(userRepository.findById("user-123")).thenReturn(Optional.of(testUser));
        when(jwtTokenProvider.generateAccessToken(testUser)).thenReturn("new-access-token");
        when(jwtTokenProvider.getAccessTokenExpirySeconds()).thenReturn(3600L);

        // Act
        RefreshTokenResponse response = authService.refresh(request);

        // Assert
        assertNotNull(response);
        assertEquals("new-access-token", response.getAccessToken());
        assertEquals(3600L, response.getExpiresIn());
        assertEquals("Bearer", response.getTokenType());
    }

    @Test
    void should_throw_InvalidTokenException_when_refresh_token_is_revoked() {
        // Arrange
        String refreshToken = "revoked-token";
        RefreshTokenRequest request = RefreshTokenRequest.builder()
            .refreshToken(refreshToken)
            .build();

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("user-123");
        when(jwtTokenProvider.validateAndGetClaims(refreshToken)).thenReturn(claims);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(
            RefreshToken.builder()
                .tokenId("token-123")
                .userId("user-123")
                .revoked(true)
                .build()
        ));

        // Act & Assert
        assertThrows(InvalidTokenException.class, () -> authService.refresh(request));
    }

    @Test
    void should_throw_InvalidTokenException_when_user_not_found_during_refresh() {
        // Arrange
        String refreshToken = "valid-refresh-token";
        RefreshTokenRequest request = RefreshTokenRequest.builder()
            .refreshToken(refreshToken)
            .build();

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("non-existent-user");
        when(claims.getId()).thenReturn("jti-123");

        when(jwtTokenProvider.validateAndGetClaims(refreshToken)).thenReturn(claims);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(
            RefreshToken.builder()
                .tokenId("token-123")
                .userId("non-existent-user")
                .revoked(false)
                .expiresAt(Instant.now().plusSeconds(86400))
                .build()
        ));
        when(userRepository.findById("non-existent-user")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(InvalidTokenException.class, () -> authService.refresh(request));
    }

    // LOGOUT TESTS

    @Test
    void should_logout_successfully_and_revoke_refresh_token() {
        // Arrange
        String refreshToken = "token-to-revoke";
        RefreshToken dbToken = RefreshToken.builder()
            .tokenId("token-123")
            .userId("user-123")
            .revoked(false)
            .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(dbToken));

        // Act
        authService.logout("user-123", refreshToken);

        // Assert
        assertTrue(dbToken.isRevoked());
        verify(refreshTokenRepository, times(1)).save(dbToken);
    }

    @Test
    void should_handle_logout_when_refresh_token_not_found() {
        // Arrange
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        // Act & Assert - should not throw
        assertDoesNotThrow(() -> authService.logout("user-123", "non-existent-token"));
    }

    // TOKEN VALIDATION TESTS

    @Test
    void should_validate_access_token_successfully() {
        // Arrange
        String accessToken = "valid-access-token";
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("user-123");
        when(claims.getId()).thenReturn("jti-123");

        when(jwtTokenProvider.validateAndGetClaims(accessToken)).thenReturn(claims);
        when(tokenBlacklistRepository.existsByTokenJti("jti-123")).thenReturn(false);

        // Act
        String userId = authService.validateAccessToken(accessToken);

        // Assert
        assertEquals("user-123", userId);
    }

    @Test
    void should_throw_InvalidTokenException_when_token_is_blacklisted() {
        // Arrange
        String accessToken = "blacklisted-token";
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("user-123");
        when(claims.getId()).thenReturn("jti-123");

        when(jwtTokenProvider.validateAndGetClaims(accessToken)).thenReturn(claims);
        when(tokenBlacklistRepository.existsByTokenJti("jti-123")).thenReturn(true);

        // Act & Assert
        assertThrows(InvalidTokenException.class, () -> authService.validateAccessToken(accessToken));
    }

    // REVOKE ALL TOKENS TEST

    @Test
    void should_revoke_all_tokens_for_user() {
        // Act
        authService.revokeAllTokens("user-123");

        // Assert
        ArgumentCaptor<String> userIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(refreshTokenRepository, times(1)).revokeAllUserTokens(
            userIdCaptor.capture(),
            any(Instant.class)
        );
        assertEquals("user-123", userIdCaptor.getValue());
    }

    // TOKEN BLACKLIST TEST

    @Test
    void should_blacklist_access_token() {
        // Arrange
        String accessToken = "token-to-blacklist";
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("user-123");
        when(claims.getId()).thenReturn("jti-123");

        when(jwtTokenProvider.validateAndGetClaims(accessToken)).thenReturn(claims);
        when(jwtTokenProvider.getExpirationTime(accessToken))
            .thenReturn(Instant.now().plusSeconds(3600));

        // Act
        authService.blacklistToken(accessToken);

        // Assert
        ArgumentCaptor<TokenBlacklist> captor = ArgumentCaptor.forClass(TokenBlacklist.class);
        verify(tokenBlacklistRepository, times(1)).save(captor.capture());
        TokenBlacklist saved = captor.getValue();
        assertEquals("jti-123", saved.getTokenJti());
        assertEquals("user-123", saved.getUserId());
    }
}
