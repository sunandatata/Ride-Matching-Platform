package com.rideshare.eta;

import com.rideshare.eta.controller.ETAController;
import com.rideshare.eta.dto.ETARequest;
import com.rideshare.eta.dto.ETAResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for ETA Service.
 * Tests end-to-end flow from controller through service layers.
 * Uses embedded Redis for testing (requires TestContainers).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379",
    "routing.provider=OSRM"
})
class ETAServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ETAController etaController;

    @Test
    void should_calculate_ETA_via_REST_endpoint() throws Exception {
        // Given
        String requestBody = """
            {
              "from_lat": 40.7128,
              "from_lng": -74.0060,
              "to_lat": 40.7580,
              "to_lng": -73.9855
            }
            """;

        // When
        MvcResult result = mockMvc.perform(post("/eta/calculate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.eta_minutes", greaterThan(0)))
            .andExpect(jsonPath("$.distance_km", greaterThan(0.0)))
            .andExpect(jsonPath("$.status", notNullValue()))
            .andReturn();

        // Then
        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).contains("eta_minutes");
        assertThat(responseBody).contains("distance_km");
        assertThat(responseBody).contains("status");
    }

    @Test
    void should_return_400_on_invalid_latitude() throws Exception {
        // Given - invalid latitude (> 90)
        String requestBody = """
            {
              "from_lat": 91.0,
              "from_lng": -74.0060,
              "to_lat": 40.7580,
              "to_lng": -73.9855
            }
            """;

        // When & Then
        mockMvc.perform(post("/eta/calculate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isBadRequest());
    }

    @Test
    void should_return_400_on_invalid_longitude() throws Exception {
        // Given - invalid longitude (> 180)
        String requestBody = """
            {
              "from_lat": 40.7128,
              "from_lng": -184.0060,
              "to_lat": 40.7580,
              "to_lng": -73.9855
            }
            """;

        // When & Then
        mockMvc.perform(post("/eta/calculate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isBadRequest());
    }

    @Test
    void should_return_400_on_missing_field() throws Exception {
        // Given - missing to_lat
        String requestBody = """
            {
              "from_lat": 40.7128,
              "from_lng": -74.0060,
              "to_lng": -73.9855
            }
            """;

        // When & Then
        mockMvc.perform(post("/eta/calculate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isBadRequest());
    }

    @Test
    void should_respond_to_health_check() throws Exception {
        // When & Then
        mockMvc.perform(get("/eta/health"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("healthy")));
    }

    @Test
    void should_calculate_ETA_for_nearby_points() throws Exception {
        // Given - points ~1km apart
        String requestBody = """
            {
              "from_lat": 40.7100,
              "from_lng": -74.0100,
              "to_lat": 40.7150,
              "to_lng": -74.0050
            }
            """;

        // When & Then
        mockMvc.perform(post("/eta/calculate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.eta_minutes", lessThan(5)))
            .andExpect(jsonPath("$.distance_km", lessThan(2.0)));
    }

    @Test
    void should_calculate_ETA_for_distant_points() throws Exception {
        // Given - points far apart
        String requestBody = """
            {
              "from_lat": 40.7128,
              "from_lng": -74.0060,
              "to_lat": 42.3601,
              "to_lng": -71.0589
            }
            """;

        // When & Then
        mockMvc.perform(post("/eta/calculate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.eta_minutes", greaterThan(100)))
            .andExpect(jsonPath("$.distance_km", greaterThan(200.0)));
    }

    @Test
    void should_return_valid_ETAResponse_structure() throws Exception {
        // Given
        String requestBody = """
            {
              "from_lat": 40.7128,
              "from_lng": -74.0060,
              "to_lat": 40.7580,
              "to_lng": -73.9855
            }
            """;

        // When & Then
        mockMvc.perform(post("/eta/calculate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.eta_minutes").isNumber())
            .andExpect(jsonPath("$.distance_km").isNumber())
            .andExpect(jsonPath("$.status").isString())
            .andExpect(jsonPath("$.status", anyOf(
                equalTo("CACHED"),
                equalTo("LIVE"),
                equalTo("ESTIMATED")
            )));
    }

    @Test
    void should_accept_boundary_coordinates() throws Exception {
        // Given - boundary coordinates
        String requestBody = """
            {
              "from_lat": -90.0,
              "from_lng": -180.0,
              "to_lat": 90.0,
              "to_lng": 180.0
            }
            """;

        // When & Then
        mockMvc.perform(post("/eta/calculate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isOk());
    }

    @Test
    void should_accept_zero_coordinates() throws Exception {
        // Given - zero coordinates (prime meridian)
        String requestBody = """
            {
              "from_lat": 0.0,
              "from_lng": 0.0,
              "to_lat": 0.01,
              "to_lng": 0.01
            }
            """;

        // When & Then
        mockMvc.perform(post("/eta/calculate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.eta_minutes", greaterThan(0)))
            .andExpect(jsonPath("$.distance_km", greaterThan(0.0)));
    }
}
