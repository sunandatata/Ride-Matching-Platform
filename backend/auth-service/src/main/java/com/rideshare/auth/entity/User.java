package com.rideshare.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;

/**
 * User entity representing a rider or driver in the system.
 * Stores user credentials, profile information, and authentication state.
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_phone_number", columnList = "phone_number", unique = true),
    @Index(name = "idx_user_type", columnList = "user_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String userId;

    @Column(nullable = false, unique = true)
    private String phoneNumber;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserType userType; // RIDER, DRIVER, ADMIN, SUPPORT

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status; // ACTIVE, INACTIVE, SUSPENDED, DELETED

    @Column(nullable = false)
    private Boolean mfaEnabled = false;

    @Column
    private String mfaPhoneNumber; // Phone number for MFA verification

    @Column(nullable = false)
    private Boolean kycVerified = false;

    @Column
    private Instant kycVerifiedAt;

    @Column
    private String profilePhotoUrl;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private Boolean deletedAt = false;

    /**
     * User type enumeration.
     */
    public enum UserType {
        RIDER, DRIVER, ADMIN, SUPPORT
    }

    /**
     * User status enumeration.
     */
    public enum UserStatus {
        ACTIVE, INACTIVE, SUSPENDED, DELETED
    }

    /**
     * Check if user account is active.
     */
    public boolean isActive() {
        return UserStatus.ACTIVE == this.status && !Boolean.TRUE.equals(this.deletedAt);
    }

    /**
     * Check if user is a driver.
     */
    public boolean isDriver() {
        return UserType.DRIVER == this.userType;
    }

    /**
     * Check if user is a rider.
     */
    public boolean isRider() {
        return UserType.RIDER == this.userType;
    }

    /**
     * Check if user is an admin or support staff.
     */
    public boolean isAdmin() {
        return UserType.ADMIN == this.userType || UserType.SUPPORT == this.userType;
    }

    public boolean isMfaEnabled() {
        return Boolean.TRUE.equals(this.mfaEnabled);
    }
}
