package com.rideshare.auth.repository;

import com.rideshare.auth.entity.TokenBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * JPA repository for TokenBlacklist entity.
 * Manages revoked JWT tokens for logout and compromise scenarios.
 */
@Repository
public interface TokenBlacklistRepository extends JpaRepository<TokenBlacklist, String> {

    /**
     * Find token by JTI (JWT ID claim).
     */
    Optional<TokenBlacklist> findByTokenJti(String tokenJti);

    /**
     * Check if token is blacklisted.
     */
    boolean existsByTokenJti(String tokenJti);

    /**
     * Delete expired blacklist entries (cleanup job).
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM TokenBlacklist tb WHERE tb.expiresAt < :now")
    void deleteExpiredEntries(@Param("now") Instant now);

    /**
     * Count blacklisted tokens for a user.
     */
    @Query("SELECT COUNT(tb) FROM TokenBlacklist tb WHERE tb.userId = :userId AND tb.expiresAt > :now")
    long countBlacklistedTokens(@Param("userId") String userId, @Param("now") Instant now);
}
