package com.rideshare.auth.repository;

import com.rideshare.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JPA repository for User entity.
 * Provides database access for user authentication and profile retrieval.
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {

    /**
     * Find user by phone number.
     */
    Optional<User> findByPhoneNumber(String phoneNumber);

    /**
     * Find active user by phone number.
     */
    @Query("SELECT u FROM User u WHERE u.phoneNumber = :phoneNumber AND u.status = 'ACTIVE' AND u.deletedAt = false")
    Optional<User> findActiveByPhoneNumber(@Param("phoneNumber") String phoneNumber);

    /**
     * Find user by email.
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if phone number exists in system.
     */
    boolean existsByPhoneNumber(String phoneNumber);

    /**
     * Check if email exists in system.
     */
    boolean existsByEmail(String email);
}
