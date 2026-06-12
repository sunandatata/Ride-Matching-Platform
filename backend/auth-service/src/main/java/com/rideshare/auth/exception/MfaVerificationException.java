package com.rideshare.auth.exception;

/**
 * Thrown when MFA verification fails.
 */
public class MfaVerificationException extends AuthException {

    public MfaVerificationException(String message) {
        super(message, "MFA_VERIFICATION_FAILED");
    }
}
