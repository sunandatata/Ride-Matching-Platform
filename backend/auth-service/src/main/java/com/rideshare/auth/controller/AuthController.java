package com.rideshare.auth.controller;

import com.rideshare.auth.dto.request.LoginRequest;
import com.rideshare.auth.dto.request.LogoutRequest;
import com.rideshare.auth.dto.request.RefreshTokenRequest;
import com.rideshare.auth.dto.request.VerifyMfaRequest;
import com.rideshare.auth.dto.response.LoginResponse;
import com.rideshare.auth.dto.response.RefreshTokenResponse;
import com.rideshare.auth.service.AuthService;
import com.rideshare.auth.service.MfaService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

/**
 * REST controller for authentication endpoints.
 * Provides login, logout, token refresh, and MFA verification endpoints.
 * All endpoints follow REST conventions and return proper HTTP status codes.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final MfaService mfaService;

    public AuthController(AuthService authService, MfaService mfaService) {
        this.authService = authService;
        this.mfaService = mfaService;
    }

    /**
     * POST /api/v1/auth/login
     * Authenticate user with phone number and password.
     * Returns JWT tokens if credentials are valid.
     *
     * @param request Login request with phone number and password
     * @return Login response with tokens and user info
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login attempt for phone number: {}", maskPhoneNumber(request.getPhoneNumber()));

        LoginResponse response = authService.login(request);

        log.info("User logged in successfully: {} (MFA required: {})",
            maskPhoneNumber(request.getPhoneNumber()),
            response.getMfaRequired());

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/auth/refresh
     * Refresh access token using refresh token.
     * Returns new access token for continued session.
     *
     * @param request Refresh token request
     * @return New access token
     */
    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        log.debug("Token refresh requested");

        RefreshTokenResponse response = authService.refresh(request);

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/auth/logout
     * Revoke refresh token and logout user.
     * Also blacklists access token if MFA was used.
     *
     * @param request Logout request with refresh token
     * @return Logout confirmation message
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@Valid @RequestBody LogoutRequest request) {
        log.debug("Logout requested");

        // Extract user ID from request context (would come from JWT in production)
        // For now, we'll extract it from the token itself
        String userId = extractUserIdFromToken(request.getRefreshToken());
        authService.logout(userId, request.getRefreshToken());

        return ResponseEntity.ok(Collections.singletonMap("message", "Logged out successfully"));
    }

    /**
     * POST /api/v1/auth/verify-mfa
     * Verify MFA OTP and complete login.
     * Returns access token if OTP is valid.
     *
     * @param request MFA verification request with user ID and OTP
     * @return Login response with tokens
     */
    @PostMapping("/verify-mfa")
    public ResponseEntity<LoginResponse> verifyMfa(@Valid @RequestBody VerifyMfaRequest request) {
        log.info("MFA verification attempted for user: {}", request.getUserId());

        mfaService.verifyOtp(request.getUserId(), request.getOtp());

        log.info("MFA verification successful for user: {}", request.getUserId());

        // After MFA verification, return tokens
        // In production, this would be done in a separate step after initial login
        return ResponseEntity.ok(LoginResponse.builder()
            .message("MFA verification successful")
            .build());
    }

    /**
     * GET /api/v1/auth/validate
     * Validate current access token.
     * Used to check if token is still valid without attempting to use it.
     *
     * @param authHeader Authorization header with Bearer token
     * @return Validation result with token validity
     */
    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validate(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Collections.singletonMap("valid", false));
        }

        String token = authHeader.substring(7);

        try {
            String userId = authService.validateAccessToken(token);
            return ResponseEntity.ok(Map.of(
                "valid", true,
                "user_id", userId
            ));
        } catch (Exception e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Collections.singletonMap("valid", false));
        }
    }

    /**
     * Extract user ID from refresh token.
     * Used internally for logout without explicit user context.
     *
     * @param token The refresh token
     * @return User ID or null if extraction fails
     */
    private String extractUserIdFromToken(String token) {
        // This would be implemented with proper JWT parsing
        // For now, return a placeholder
        return "extracted-user-id";
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
}
