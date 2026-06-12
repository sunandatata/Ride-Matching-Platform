package com.rideshare.auth.exception;

/**
 * Thrown when login credentials are invalid.
 */
public class InvalidCredentialsException extends AuthException {

    public InvalidCredentialsException() {
        super("Invalid phone number or password", "AUTHENTICATION_FAILED");
    }
}
