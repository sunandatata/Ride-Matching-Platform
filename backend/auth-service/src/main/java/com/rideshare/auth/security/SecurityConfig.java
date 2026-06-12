package com.rideshare.auth.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

/**
 * Spring Security configuration for authentication service.
 * Configures stateless JWT-based security with CORS support.
 * Disables session management for microservice architecture.
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    /**
     * Configure HTTP security with JWT token-based authentication.
     * Uses stateless session policy for horizontal scaling.
     *
     * @param http HttpSecurity builder
     * @return Configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF as API is stateless
            .csrf().disable()

            // Enable CORS
            .cors().and()

            // Use stateless session management (no server-side session)
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()

            // Configure request security
            .authorizeHttpRequests(authorize -> authorize
                // Public endpoints (no authentication required)
                .requestMatchers("/api/v1/auth/login").permitAll()
                .requestMatchers("/api/v1/auth/refresh").permitAll()
                .requestMatchers("/api/v1/auth/verify-mfa").permitAll()
                .requestMatchers("/api/v1/auth/logout").permitAll()
                .requestMatchers("/api/v1/auth/validate").permitAll()
                .requestMatchers("/health").permitAll()
                .requestMatchers("/actuator/**").permitAll()

                // All other requests require authentication
                .anyRequest().authenticated()
            )

            // Remove default login page and filters
            .httpBasic().disable()
            .formLogin().disable();

        log.info("Spring Security configured with stateless JWT authentication");
        return http.build();
    }

    /**
     * Configure CORS settings for API access.
     * Allows requests from approved origins with proper headers.
     *
     * @return CorsConfigurationSource with CORS policies
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Allow requests from these origins
        config.setAllowedOrigins(Arrays.asList(
            "http://localhost:3000",
            "http://localhost:3001",
            "http://localhost:3002",
            "https://api-dev.rideshare.local",
            "https://api-staging.rideshare.local",
            "https://api.rideshare.local"
        ));

        // Allow standard HTTP methods
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // Allow these headers
        config.setAllowedHeaders(Arrays.asList("*"));

        // Allow credentials in cross-origin requests
        config.setAllowCredentials(true);

        // Cache preflight response for 1 hour
        config.setMaxAge(3600L);

        // Expose Authorization header to client
        config.setExposedHeaders(Collections.singletonList("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }

    /**
     * Password encoder bean using BCrypt.
     * Note: PasswordHasher component is preferred for explicit usage.
     * This bean is provided for Spring Security integration.
     *
     * @return BCryptPasswordEncoder with strength 12
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
