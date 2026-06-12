package com.rideshare.auth.security;

import com.rideshare.auth.entity.User;
import com.rideshare.auth.exception.InvalidTokenException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JWT token generation and validation component.
 * Handles access token and refresh token creation and validation.
 * Implements HS256 signing algorithm for high-performance validation.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpiryMillis;
    private final long refreshTokenExpiryMillis;
    private final String issuer;
    private final String audience;

    public JwtTokenProvider(
            @Value("${auth.jwt.secret}") String secret,
            @Value("${auth.jwt.access-token-expiry-hours:1}") int accessTokenExpiryHours,
            @Value("${auth.jwt.refresh-token-expiry-days:7}") int refreshTokenExpiryDays,
            @Value("${auth.jwt.issuer:rideshare.local}") String issuer,
            @Value("${auth.jwt.audience:rideshare-api}") String audience) {

        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiryMillis = accessTokenExpiryHours * 3600L * 1000L;
        this.refreshTokenExpiryMillis = refreshTokenExpiryDays * 24L * 3600L * 1000L;
        this.issuer = issuer;
        this.audience = audience;

        log.info("JWT Token Provider initialized with access token expiry: {} hours", accessTokenExpiryHours);
    }

    /**
     * Generate access token for user.
     * Includes user ID, type, and scopes in the token.
     *
     * @param user The user entity
     * @return JWT access token
     */
    public String generateAccessToken(User user) {
        return generateAccessToken(user.getUserId(), user.getUserType().name(), user.isAdmin());
    }

    /**
     * Generate access token with custom claims.
     *
     * @param userId The user ID
     * @param userType The user type (RIDER, DRIVER, ADMIN, SUPPORT)
     * @param isAdmin Whether user is admin
     * @return JWT access token
     */
    public String generateAccessToken(String userId, String userType, boolean isAdmin) {
        Instant now = Instant.now();
        Instant expiryTime = now.plusMillis(accessTokenExpiryMillis);

        Map<String, Object> claims = new HashMap<>();
        claims.put("type", userType);
        claims.put("scope", buildScopes(userType, isAdmin));

        return Jwts.builder()
                .subject(userId)
                .claims(claims)
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiryTime))
                .id(UUID.randomUUID().toString()) // jti claim for token identification
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Generate refresh token for user.
     * Refresh tokens are used to obtain new access tokens.
     *
     * @param userId The user ID
     * @param deviceId The device identifier
     * @return JWT refresh token
     */
    public String generateRefreshToken(String userId, String deviceId) {
        Instant now = Instant.now();
        Instant expiryTime = now.plusMillis(refreshTokenExpiryMillis);

        Map<String, Object> claims = new HashMap<>();
        claims.put("device_id", deviceId);
        claims.put("type", "refresh");

        return Jwts.builder()
                .subject(userId)
                .claims(claims)
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiryTime))
                .id(UUID.randomUUID().toString()) // jti claim
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Validate and extract claims from JWT token.
     *
     * @param token The JWT token
     * @return Claims from the token
     * @throws InvalidTokenException if token is invalid or expired
     */
    public Claims validateAndGetClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("JWT token has expired: {}", e.getMessage());
            throw new InvalidTokenException("Token has expired", e);
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT token: {}", e.getMessage());
            throw new InvalidTokenException("Unsupported token format", e);
        } catch (MalformedJwtException e) {
            log.warn("Malformed JWT token: {}", e.getMessage());
            throw new InvalidTokenException("Malformed token", e);
        } catch (SignatureException e) {
            log.warn("Invalid JWT signature: {}", e.getMessage());
            throw new InvalidTokenException("Invalid token signature", e);
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims string is empty: {}", e.getMessage());
            throw new InvalidTokenException("Empty token", e);
        }
    }

    /**
     * Extract user ID from token without full validation.
     * Used for blacklist checks.
     *
     * @param token The JWT token
     * @return User ID or null if token is invalid
     */
    public String getUserIdFromToken(String token) {
        try {
            Claims claims = validateAndGetClaims(token);
            return claims.getSubject();
        } catch (InvalidTokenException e) {
            log.debug("Failed to extract user ID from token");
            return null;
        }
    }

    /**
     * Extract JTI (JWT ID) from token.
     *
     * @param token The JWT token
     * @return JTI claim value
     */
    public String getJtiFromToken(String token) {
        try {
            Claims claims = validateAndGetClaims(token);
            return claims.getId();
        } catch (InvalidTokenException e) {
            return null;
        }
    }

    /**
     * Get token expiration time.
     *
     * @param token The JWT token
     * @return Expiration instant
     */
    public Instant getExpirationTime(String token) {
        try {
            Claims claims = validateAndGetClaims(token);
            return claims.getExpiration().toInstant();
        } catch (InvalidTokenException e) {
            return null;
        }
    }

    /**
     * Get access token expiry duration in seconds.
     */
    public long getAccessTokenExpirySeconds() {
        return accessTokenExpiryMillis / 1000;
    }

    /**
     * Build scope string based on user type.
     * Determines what operations user can perform.
     *
     * @param userType The user type
     * @param isAdmin Whether user is admin
     * @return Scope string
     */
    private String buildScopes(String userType, boolean isAdmin) {
        StringBuilder scopes = new StringBuilder();

        if (isAdmin) {
            scopes.append("admin:read admin:write users:read users:write rides:read rides:write");
        } else if ("RIDER".equals(userType)) {
            scopes.append("rides:read rides:write profile:read profile:write payments:read");
        } else if ("DRIVER".equals(userType)) {
            scopes.append("rides:read profile:read profile:write earnings:read locations:write");
        }

        return scopes.toString();
    }
}
