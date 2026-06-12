package com.rideshare.auth.service;

import com.rideshare.auth.entity.User;
import com.rideshare.auth.exception.MfaVerificationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * MFA (Multi-Factor Authentication) service for phone-based verification.
 * Generates and validates one-time passwords (OTP) sent via SMS.
 * Stores OTP in Redis with 5-minute expiration for security.
 */
@Slf4j
@Service
public class MfaService {

    private static final String OTP_PREFIX = "mfa:otp:";
    private static final String OTP_ATTEMPTS_PREFIX = "mfa:attempts:";
    private static final int OTP_LENGTH = 6;
    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final int MAX_ATTEMPTS = 3;

    private final RedisTemplate<String, String> redisTemplate;
    private final SecureRandom secureRandom;

    @Value("${auth.mfa.enabled:true}")
    private boolean mfaEnabled;

    public MfaService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.secureRandom = new SecureRandom();
    }

    /**
     * Generate and send OTP to user's MFA phone number.
     * OTP is stored in Redis with expiration.
     *
     * @param user The user to send OTP to
     * @return OTP value (for testing purposes only in production would just log)
     */
    public String generateAndSendOtp(User user) {
        if (!mfaEnabled || !user.isMfaEnabled()) {
            throw new MfaVerificationException("MFA is not enabled for this user");
        }

        if (user.getMfaPhoneNumber() == null || user.getMfaPhoneNumber().isEmpty()) {
            throw new MfaVerificationException("MFA phone number not configured");
        }

        // Generate 6-digit OTP
        String otp = generateOtp();

        // Store OTP in Redis with expiration
        String key = OTP_PREFIX + user.getUserId();
        redisTemplate.opsForValue().set(key, otp, OTP_EXPIRY_MINUTES, TimeUnit.MINUTES);

        // Reset attempt counter
        String attemptsKey = OTP_ATTEMPTS_PREFIX + user.getUserId();
        redisTemplate.delete(attemptsKey);

        // Log for audit (in production, would send via SMS provider)
        log.info("OTP generated for user: {} (masked phone: {})",
            user.getUserId(),
            maskPhoneNumber(user.getMfaPhoneNumber()));

        return otp; // Return only for testing/development
    }

    /**
     * Verify OTP for user.
     * Checks attempt count to prevent brute-force attacks.
     *
     * @param userId The user ID
     * @param otp The OTP to verify
     * @return True if OTP is valid
     * @throws MfaVerificationException if OTP is invalid or expired
     */
    public boolean verifyOtp(String userId, String otp) {
        String key = OTP_PREFIX + userId;
        String attemptsKey = OTP_ATTEMPTS_PREFIX + userId;

        // Check attempt count
        String attemptsStr = redisTemplate.opsForValue().get(attemptsKey);
        int attempts = attemptsStr != null ? Integer.parseInt(attemptsStr) : 0;

        if (attempts >= MAX_ATTEMPTS) {
            redisTemplate.delete(key); // Invalidate OTP
            throw new MfaVerificationException("Too many failed verification attempts. OTP expired.");
        }

        // Retrieve stored OTP
        String storedOtp = redisTemplate.opsForValue().get(key);

        if (storedOtp == null) {
            throw new MfaVerificationException("OTP has expired. Please request a new one.");
        }

        // Verify OTP (using constant-time comparison to prevent timing attacks)
        if (!constantTimeEquals(otp, storedOtp)) {
            // Increment attempt counter
            redisTemplate.opsForValue().increment(attemptsKey);
            redisTemplate.expire(attemptsKey, OTP_EXPIRY_MINUTES, TimeUnit.MINUTES);
            throw new MfaVerificationException("Invalid OTP. Please try again.");
        }

        // OTP verified successfully - clean up
        redisTemplate.delete(key);
        redisTemplate.delete(attemptsKey);

        log.info("MFA verification successful for user: {}", userId);
        return true;
    }

    /**
     * Generate a 6-digit random OTP.
     *
     * @return 6-digit OTP string
     */
    private String generateOtp() {
        int otp = secureRandom.nextInt(999999);
        return String.format("%06d", otp);
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     * Compares strings in constant time regardless of where they differ.
     *
     * @param a First string
     * @param b Second string
     * @return True if strings are equal
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }

        byte[] aBytes = a.getBytes();
        byte[] bBytes = b.getBytes();

        int result = aBytes.length ^ bBytes.length;
        int minLength = Math.min(aBytes.length, bBytes.length);

        for (int i = 0; i < minLength; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }

        return result == 0;
    }

    /**
     * Mask phone number for logging (e.g., +1234567890 → +123****7890).
     *
     * @param phoneNumber The phone number to mask
     * @return Masked phone number
     */
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 8) {
            return phoneNumber;
        }
        int len = phoneNumber.length();
        return phoneNumber.substring(0, 3) + "*".repeat(len - 7) + phoneNumber.substring(len - 4);
    }

    /**
     * Check if MFA is enabled globally.
     */
    public boolean isMfaEnabled() {
        return mfaEnabled;
    }
}
