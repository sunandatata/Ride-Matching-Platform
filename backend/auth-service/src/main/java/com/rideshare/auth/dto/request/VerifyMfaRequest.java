package com.rideshare.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MFA verification request DTO.
 * Contains OTP for phone-based verification.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerifyMfaRequest {

    @NotBlank(message = "User ID is required")
    private String userId;

    @NotBlank(message = "OTP is required")
    @Pattern(regexp = "^[0-9]{6}$", message = "OTP must be 6 digits")
    private String otp;
}
