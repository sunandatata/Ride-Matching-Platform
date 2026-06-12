package com.rideshare.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Authentication Service main application entry point.
 * Handles JWT token generation, validation, refresh, and MFA verification.
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.rideshare.auth", "com.rideshare.shared"})
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
