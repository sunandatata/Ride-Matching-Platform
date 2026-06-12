package com.rideshare.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;

import java.time.Instant;

/**
 * Token blacklist entity for revoked JWT tokens.
 * Implements token revocation on logout or compromise.
 */
@Entity
@Table(name = "token_blacklist", indexes = {
    @Index(name = "idx_token_jti", columnList = "token_jti", unique = true),
    @Index(name = "idx_expires_at", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenBlacklist {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String tokenJti; // JWT ID (jti claim)

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private Instant expiresAt; // Token expiration time

    @Column(nullable = false)
    private String reason; // Reason for blacklisting

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant blacklistedAt;

    /**
     * Check if token is still in blacklist (not yet expired).
     */
    public boolean isStillBlacklisted() {
        return Instant.now().isBefore(expiresAt);
    }
}
