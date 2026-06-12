package com.rideshare.auth.exception;

/**
 * Thrown when user is not found in the system.
 */
public class UserNotFoundException extends AuthException {

    public UserNotFoundException(String phoneNumber) {
        super("User not found: " + phoneNumber, "USER_NOT_FOUND");
    }
}
