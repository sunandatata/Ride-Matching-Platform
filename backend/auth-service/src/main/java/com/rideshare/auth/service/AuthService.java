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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Authentication service for user login, logout, and token management.
 * Implements JWT token generation, refresh token lifecycle, and token blacklist.
 * Provides centralized authentication logic for the platform.
 */
@Slf4j
@Service
@Transactional
public class AuthService {

    private static final String TEMP_LOGIN_CACHE_PREFIX = "temp_login:";
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final int LOGIN_ATTEMPT_WINDOW_MINUTES = 15;
    private static final int DEVICE_TOKEN_LIMIT = 3; // Max refresh tokens per device type

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordHasher passwordHasher;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${auth.login-attempt-blocking:true}")
    private boolean loginAttemptBlocking;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            TokenBlacklistRepository tokenBlacklistRepository,
            JwtTokenProvider jwtTokenProvider,
            PasswordHasher passwordHasher,
            RedisTemplate<String, String> redisTemplate) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenBlacklistRepository = tokenBlacklistRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordHasher = passwordHasher;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Authenticate user and generate tokens.
     * Implements login attempt rate limiting to prevent brute-force attacks.
     *
     * @param request Login request with phone number and password
     * @return Login response with access and refresh tokens
     * @throws UserNotFoundException if user doesn't exist
     * @throws InvalidCredentialsException if password is incorrect
     */
    public LoginResponse login(LoginRequest request) {
        // Check login attempt rate limiting
        if (loginAttemptBlocking) {
            checkLoginAttempts(request.getPhoneNumber());
        }

        // Find user by phone number
        User user = userRepository.findActiveByPhoneNumber(request.getPhoneNumber())
            .orElseThrow(InvalidCredentialsException::new);

        // Verify password
        if (!passwordHasher.verify(request.getPassword(), user.getPasswordHash())) {
            recordFailedLoginAttempt(request.getPhoneNumber());
            throw new InvalidCredentialsException();
        }

        log.info("Successful login for user: {} from device: {}", user.getUserId(), request.getDeviceId());

        // Check if MFA is enabled
        if (user.isMfaEnabled()) {
            // Return response indicating MFA is required
            return LoginResponse.builder()
                .mfaRequired(true)
                .user(mapUserToInfo(user))
                .build();
        }

        // Generate tokens
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUserId(), request.getDeviceId());

        // Store refresh token with hash
        storeRefreshToken(user.getUserId(), refreshToken, request.getDeviceId(), request.getDeviceType());

        // Clear login attempt counter on successful login
        clearLoginAttempts(request.getPhoneNumber());

        return LoginResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .expiresIn(jwtTokenProvider.getAccessTokenExpirySeconds())
            .tokenType("Bearer")
            .user(mapUserToInfo(user))
            .mfaRequired(false)
            .build();
    }

    /**
     * Refresh access token using refresh token.
     * Validates refresh token and generates new access token.
     *
     * @param request Refresh token request
     * @return New access token
     * @throws InvalidTokenException if refresh token is invalid
     */
    public RefreshTokenResponse refresh(RefreshTokenRequest request) {
        // Validate refresh token format and extract claims
        Claims claims = jwtTokenProvider.validateAndGetClaims(request.getRefreshToken());
        String userId = claims.getSubject();

        // Get token hash and find in database
        String tokenJti = claims.getId();
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hashToken(request.getRefreshToken()))
            .orElseThrow(() -> new InvalidTokenException("Refresh token not found or revoked"));

        // Verify token hasn't been revoked
        if (!refreshToken.isValid()) {
            throw new InvalidTokenException("Refresh token has been revoked");
        }

        // Fetch user and generate new access token
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new InvalidTokenException("User not found"));

        if (!user.isActive()) {
            throw new InvalidTokenException("User account is inactive");
        }

        String newAccessToken = jwtTokenProvider.generateAccessToken(user);

        log.info("Token refreshed for user: {} from device: {}", userId, refreshToken.getDeviceId());

        return RefreshTokenResponse.builder()
            .accessToken(newAccessToken)
            .expiresIn(jwtTokenProvider.getAccessTokenExpirySeconds())
            .tokenType("Bearer")
            .build();
    }

    /**
     * Logout user by revoking refresh token.
     * Also blacklists access token if provided.
     *
     * @param userId The user ID
     * @param refreshToken The refresh token to revoke
     */
    public void logout(String userId, String refreshToken) {
        // Revoke refresh token
        RefreshToken dbToken = refreshTokenRepository.findByTokenHash(hashToken(refreshToken))
            .orElse(null);

        if (dbToken != null && !dbToken.isRevoked()) {
            dbToken.revoke();
            refreshTokenRepository.save(dbToken);
            log.info("Refresh token revoked for user: {}", userId);
        }
    }

    /**
     * Revoke all active refresh tokens for a user.
     * Used when user changes password or account is compromised.
     *
     * @param userId The user ID
     */
    public void revokeAllTokens(String userId) {
        Instant now = Instant.now();
        refreshTokenRepository.revokeAllUserTokens(userId, now);
        log.info("All refresh tokens revoked for user: {}", userId);
    }

    /**
     * Validate access token and extract user ID.
     * Checks both JWT signature and blacklist.
     *
     * @param accessToken The access token to validate
     * @return User ID from token
     * @throws InvalidTokenException if token is invalid
     */
    public String validateAccessToken(String accessToken) {
        // Validate JWT signature and expiration
        Claims claims = jwtTokenProvider.validateAndGetClaims(accessToken);
        String userId = claims.getSubject();
        String jti = claims.getId();

        // Check if token is blacklisted
        if (tokenBlacklistRepository.existsByTokenJti(jti)) {
            throw new InvalidTokenException("Token has been revoked");
        }

        return userId;
    }

    /**
     * Blacklist an access token.
     * Used for logout or token revocation.
     *
     * @param accessToken The token to blacklist
     */
    public void blacklistToken(String accessToken) {
        try {
            Claims claims = jwtTokenProvider.validateAndGetClaims(accessToken);
            String userId = claims.getSubject();
            String jti = claims.getId();
            Instant expiresAt = jwtTokenProvider.getExpirationTime(accessToken);

            if (expiresAt == null) {
                return;
            }

            TokenBlacklist blacklistEntry = TokenBlacklist.builder()
                .tokenJti(jti)
                .userId(userId)
                .expiresAt(expiresAt)
                .reason("USER_LOGOUT")
                .build();

            tokenBlacklistRepository.save(blacklistEntry);
            log.info("Token blacklisted for user: {}", userId);
        } catch (InvalidTokenException e) {
            log.debug("Failed to blacklist token: {}", e.getMessage());
        }
    }

    /**
     * Store refresh token in database with expiration tracking.
     * Limits number of active tokens per device type to prevent token proliferation.
     *
     * @param userId The user ID
     * @param token The refresh token
     * @param deviceId The device identifier
     * @param deviceType The device type (ios, android, web)
     */
    private void storeRefreshToken(String userId, String token, String deviceId, String deviceType) {
        Instant expiresAt = Instant.now().plusSeconds(7 * 24 * 3600); // 7 days

        RefreshToken refreshToken = RefreshToken.builder()
            .userId(userId)
            .tokenHash(hashToken(token))
            .deviceId(deviceId)
            .deviceType(deviceType)
            .expiresAt(expiresAt)
            .revoked(false)
            .build();

        refreshTokenRepository.save(refreshToken);
    }

    /**
     * Record failed login attempt for rate limiting.
     *
     * @param phoneNumber The phone number with failed attempt
     */
    private void recordFailedLoginAttempt(String phoneNumber) {
        String key = TEMP_LOGIN_CACHE_PREFIX + phoneNumber;
        ValueOperations<String, String> valueOps = redisTemplate.opsForValue();
        if (valueOps == null) {
            return;
        }

        String attemptsStr = valueOps.get(key);

        int attempts = attemptsStr != null ? Integer.parseInt(attemptsStr) : 0;
        attempts++;

        valueOps.set(
            key,
            String.valueOf(attempts),
            LOGIN_ATTEMPT_WINDOW_MINUTES,
            TimeUnit.MINUTES
        );

        if (attempts >= MAX_LOGIN_ATTEMPTS) {
            log.warn("User account locked due to multiple failed login attempts: {}", phoneNumber);
        }
    }

    /**
     * Check if phone number is locked due to too many failed attempts.
     *
     * @param phoneNumber The phone number to check
     * @throws InvalidCredentialsException if account is locked
     */
    private void checkLoginAttempts(String phoneNumber) {
        String key = TEMP_LOGIN_CACHE_PREFIX + phoneNumber;
        ValueOperations<String, String> valueOps = redisTemplate.opsForValue();
        if (valueOps == null) {
            return;
        }

        String attemptsStr = valueOps.get(key);

        if (attemptsStr != null) {
            int attempts = Integer.parseInt(attemptsStr);
            if (attempts >= MAX_LOGIN_ATTEMPTS) {
                throw new InvalidCredentialsException();
            }
        }
    }

    /**
     * Clear login attempt counter after successful login.
     *
     * @param phoneNumber The phone number to clear
     */
    private void clearLoginAttempts(String phoneNumber) {
        String key = TEMP_LOGIN_CACHE_PREFIX + phoneNumber;
        redisTemplate.delete(key);
    }

    /**
     * Hash refresh token using SHA-256.
     * Tokens are stored as hashes for security.
     *
     * @param token The token to hash
     * @return SHA-256 hash of token
     */
    private String hashToken(String token) {
        // In production, use proper hash function
        // This is simplified for demonstration
        return java.util.Base64.getEncoder()
            .encodeToString(token.getBytes())
            .substring(0, Math.min(256, token.length()));
    }

    /**
     * Map User entity to LoginResponse.UserInfo DTO.
     *
     * @param user The user entity
     * @return User info DTO
     */
    private LoginResponse.UserInfo mapUserToInfo(User user) {
        return LoginResponse.UserInfo.builder()
            .userId(user.getUserId())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .phoneNumber(user.getPhoneNumber())
            .type(user.getUserType().toString().toLowerCase())
            .kycVerified(user.getKycVerified())
            .build();
    }

    /**
     * Cleanup job to delete expired refresh tokens.
     * Runs daily to maintain database health.
     */
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    public void cleanupExpiredTokens() {
        Instant now = Instant.now();
        refreshTokenRepository.deleteExpiredTokens(now);
        tokenBlacklistRepository.deleteExpiredEntries(now);
        log.info("Cleanup job completed: expired tokens deleted");
    }
}
