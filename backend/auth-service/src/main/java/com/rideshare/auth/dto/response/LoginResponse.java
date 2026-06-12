package com.rideshare.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Login response DTO containing tokens and user information.
 * Follows OAuth2 response format for standard client compatibility.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("expires_in")
    private Long expiresIn; // Token lifetime in seconds (3600 for 1 hour)

    @JsonProperty("token_type")
    private String tokenType; // "Bearer"

    private UserInfo user;

    @JsonProperty("mfa_required")
    private Boolean mfaRequired; // If true, MFA verification required before full login

    private String message;

    /**
     * User information included in login response.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserInfo {

        @JsonProperty("user_id")
        private String userId;

        @JsonProperty("first_name")
        private String firstName;

        @JsonProperty("last_name")
        private String lastName;

        @JsonProperty("phone_number")
        private String phoneNumber;

        private String type; // rider, driver, admin, support

        @JsonProperty("kyc_verified")
        private Boolean kycVerified;
    }
}
