package com.rideshare.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;

import java.time.Instant;

/**
 * Refresh token entity for token renewal.
 * Stores issued refresh tokens with expiration and revocation tracking.
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
    @Index(name = "idx_token_hash", columnList = "token_hash", unique = true),
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_expires_at", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String tokenId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false, unique = true)
    private String tokenHash; // SHA-256 hash of the actual token

    @Column(nullable = false)
    private String deviceId; // Device identifier for token tracking

    @Column(nullable = false)
    private String deviceType; // ios, android, web

    @Column(nullable = false)
    private Boolean revoked = false;

    @Column
    private Instant revokedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant issuedAt;

    /**
     * Check if token is still valid.
     */
    public boolean isValid() {
        return !revoked && Instant.now().isBefore(expiresAt);
    }

    public boolean isRevoked() {
        return Boolean.TRUE.equals(this.revoked);
    }

    /**
     * Check if token is expired.
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Mark token as revoked.
     */
    public void revoke() {
        this.revoked = true;
        this.revokedAt = Instant.now();
    }
}
