package com.rideshare.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rideshare.auth.dto.request.LoginRequest;
import com.rideshare.auth.dto.request.LogoutRequest;
import com.rideshare.auth.dto.request.RefreshTokenRequest;
import com.rideshare.auth.dto.response.LoginResponse;
import com.rideshare.auth.dto.response.RefreshTokenResponse;
import com.rideshare.auth.exception.InvalidCredentialsException;
import com.rideshare.auth.service.AuthService;
import com.rideshare.auth.service.MfaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AuthController.
 * Tests REST endpoints with Spring MockMvc for request/response handling.
 * Covers validation, error handling, and HTTP status codes.
 */
@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private MfaService mfaService;

    // LOGIN TESTS

    @Test
    void should_return_200_and_tokens_when_login_successful() throws Exception {
        // Arrange
        LoginRequest request = LoginRequest.builder()
            .phoneNumber("+1234567890")
            .password("password123")
            .deviceId("device-456")
            .deviceType("ios")
            .build();

        LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder()
            .userId("user-123")
            .firstName("John")
            .lastName("Doe")
            .phoneNumber("+1234567890")
            .type("rider")
            .kycVerified(true)
            .build();

        LoginResponse response = LoginResponse.builder()
            .accessToken("access-token-123")
            .refreshToken("refresh-token-123")
            .expiresIn(3600L)
            .tokenType("Bearer")
            .user(userInfo)
            .mfaRequired(false)
            .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").value("access-token-123"))
            .andExpect(jsonPath("$.refresh_token").value("refresh-token-123"))
            .andExpect(jsonPath("$.expires_in").value(3600L))
            .andExpect(jsonPath("$.token_type").value("Bearer"))
            .andExpect(jsonPath("$.user.first_name").value("John"))
            .andExpect(jsonPath("$.mfa_required").value(false));
    }

    @Test
    void should_return_400_when_phone_number_is_missing() throws Exception {
        // Arrange
        String requestBody = """
            {
                "password": "password123",
                "deviceId": "device-456",
                "deviceType": "ios"
            }
            """;

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest());
    }

    @Test
    void should_return_400_when_password_is_missing() throws Exception {
        // Arrange
        String requestBody = """
            {
                "phoneNumber": "+1234567890",
                "deviceId": "device-456",
                "deviceType": "ios"
            }
            """;

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest());
    }

    @Test
    void should_return_400_when_phone_number_format_is_invalid() throws Exception {
        // Arrange
        LoginRequest request = LoginRequest.builder()
            .phoneNumber("invalid-phone")
            .password("password123")
            .deviceId("device-456")
            .deviceType("ios")
            .build();

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void should_return_401_when_credentials_are_invalid() throws Exception {
        // Arrange
        LoginRequest request = LoginRequest.builder()
            .phoneNumber("+1234567890")
            .password("wrongpassword")
            .deviceId("device-456")
            .deviceType("ios")
            .build();

        when(authService.login(any(LoginRequest.class)))
            .thenThrow(new InvalidCredentialsException());

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void should_return_200_and_mfa_required_when_user_has_mfa() throws Exception {
        // Arrange
        LoginRequest request = LoginRequest.builder()
            .phoneNumber("+1234567890")
            .password("password123")
            .deviceId("device-456")
            .deviceType("ios")
            .build();

        LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder()
            .userId("user-123")
            .firstName("John")
            .lastName("Doe")
            .phoneNumber("+1234567890")
            .type("rider")
            .build();

        LoginResponse response = LoginResponse.builder()
            .mfaRequired(true)
            .user(userInfo)
            .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mfa_required").value(true))
            .andExpect(jsonPath("$.access_token").doesNotExist());
    }

    // REFRESH TOKEN TESTS

    @Test
    void should_return_200_and_new_token_when_refresh_successful() throws Exception {
        // Arrange
        RefreshTokenRequest request = RefreshTokenRequest.builder()
            .refreshToken("refresh-token-123")
            .build();

        RefreshTokenResponse response = RefreshTokenResponse.builder()
            .accessToken("new-access-token")
            .expiresIn(3600L)
            .tokenType("Bearer")
            .build();

        when(authService.refresh(any(RefreshTokenRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").value("new-access-token"))
            .andExpect(jsonPath("$.expires_in").value(3600L));
    }

    @Test
    void should_return_400_when_refresh_token_missing() throws Exception {
        // Arrange
        String requestBody = "{}";

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest());
    }

    // LOGOUT TESTS

    @Test
    void should_return_200_when_logout_successful() throws Exception {
        // Arrange
        LogoutRequest request = LogoutRequest.builder()
            .refreshToken("refresh-token-123")
            .build();

        doNothing().when(authService).logout(anyString(), anyString());

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }

    // VALIDATE TOKEN TESTS

    @Test
    void should_return_200_and_valid_true_when_token_is_valid() throws Exception {
        // Arrange
        when(authService.validateAccessToken("valid-token")).thenReturn("user-123");

        // Act & Assert
        mockMvc.perform(get("/api/v1/auth/validate")
                .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.user_id").value("user-123"));
    }

    @Test
    void should_return_401_when_authorization_header_missing() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/auth/validate"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    void should_return_401_when_token_is_invalid() throws Exception {
        // Arrange
        when(authService.validateAccessToken("invalid-token"))
            .thenThrow(new IllegalArgumentException("Invalid token"));

        // Act & Assert
        mockMvc.perform(get("/api/v1/auth/validate")
                .header("Authorization", "Bearer invalid-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.valid").value(false));
    }
}
