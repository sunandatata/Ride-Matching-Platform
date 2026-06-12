package com.rideshare.auth.repository;

import com.rideshare.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JPA repository for RefreshToken entity.
 * Manages refresh token lifecycle and revocation.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    /**
     * Find refresh token by hash.
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Find all active tokens for a user.
     */
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.userId = :userId AND rt.revoked = false AND rt.expiresAt > :now")
    List<RefreshToken> findActiveTokensByUserId(@Param("userId") String userId, @Param("now") Instant now);

    /**
     * Find tokens by user and device.
     */
    Optional<RefreshToken> findByUserIdAndDeviceId(String userId, String deviceId);

    /**
     * Revoke all tokens for a user.
     */
    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken rt SET rt.revoked = true, rt.revokedAt = :now WHERE rt.userId = :userId AND rt.revoked = false")
    void revokeAllUserTokens(@Param("userId") String userId, @Param("now") Instant now);

    /**
     * Delete expired tokens (cleanup job).
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    void deleteExpiredTokens(@Param("now") Instant now);

    /**
     * Count active tokens for a user.
     */
    @Query("SELECT COUNT(rt) FROM RefreshToken rt WHERE rt.userId = :userId AND rt.revoked = false AND rt.expiresAt > :now")
    long countActiveTokens(@Param("userId") String userId, @Param("now") Instant now);
}
