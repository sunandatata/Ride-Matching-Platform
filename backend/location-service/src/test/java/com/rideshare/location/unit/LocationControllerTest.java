package com.rideshare.location.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rideshare.location.controller.LocationController;
import com.rideshare.location.dto.request.LocationUpdateRequest;
import com.rideshare.location.dto.response.LocationResponse;
import com.rideshare.location.dto.response.NearbyDriverResponse;
import com.rideshare.location.service.LocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for LocationController.
 * Tests REST endpoints and request/response handling.
 */
@WebMvcTest(LocationController.class)
@DisplayName("LocationController Tests")
class LocationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LocationService locationService;

    private LocationUpdateRequest locationRequest;

    @BeforeEach
    void setUp() {
        locationRequest = LocationUpdateRequest.builder()
            .driverId("driver-123")
            .lat(40.7128)
            .lng(-74.0060)
            .heading(180)
            .speed(15.5)
            .timestamp(Instant.now())
            .source("gps")
            .build();
    }

    @Test
    @DisplayName("PUT /drivers/location should accept location update")
    void testUpdateDriverLocation() throws Exception {
        mockMvc.perform(put("/api/v1/locations/drivers/location")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(locationRequest)))
            .andExpect(status().isAccepted());

        verify(locationService, times(1)).updateLocation(any());
    }

    @Test
    @DisplayName("PUT /drivers/location should reject invalid latitude")
    void testUpdateLocationInvalidLatitude() throws Exception {
        locationRequest.setLat(91.0);

        mockMvc.perform(put("/api/v1/locations/drivers/location")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(locationRequest)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /drivers/location should reject invalid longitude")
    void testUpdateLocationInvalidLongitude() throws Exception {
        locationRequest.setLng(181.0);

        mockMvc.perform(put("/api/v1/locations/drivers/location")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(locationRequest)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /drivers/location should reject missing driver ID")
    void testUpdateLocationMissingDriverId() throws Exception {
        locationRequest.setDriverId(null);

        mockMvc.perform(put("/api/v1/locations/drivers/location")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(locationRequest)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /nearby should return nearby drivers")
    void testFindNearbyDrivers() throws Exception {
        NearbyDriverResponse response = NearbyDriverResponse.builder()
            .lat(40.7128)
            .lng(-74.0060)
            .radiusKm(5)
            .drivers(List.of(
                NearbyDriverResponse.DriverLocationInfo.builder()
                    .driverId("driver-1")
                    .latitude(40.7130)
                    .longitude(-74.0062)
                    .distanceMeters(250.0)
                    .build()
            ))
            .count(1)
            .build();

        when(locationService.findNearbyDrivers(40.7128, -74.0060, 5))
            .thenReturn(response);

        mockMvc.perform(get("/api/v1/locations/nearby")
            .param("lat", "40.7128")
            .param("lng", "-74.0060")
            .param("radiusKm", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.drivers[0].driverId").value("driver-1"));

        verify(locationService, times(1)).findNearbyDrivers(40.7128, -74.0060, 5);
    }

    @Test
    @DisplayName("GET /drivers/{id}/location should return current location")
    void testGetDriverLocation() throws Exception {
        LocationResponse response = LocationResponse.builder()
            .driverId("driver-123")
            .latitude(40.7128)
            .longitude(-74.0060)
            .isOnline(true)
            .build();

        when(locationService.getDriverLocation("driver-123"))
            .thenReturn(Optional.of(response));

        mockMvc.perform(get("/api/v1/locations/drivers/driver-123/location"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.driverId").value("driver-123"))
            .andExpect(jsonPath("$.latitude").value(40.7128));
    }

    @Test
    @DisplayName("GET /drivers/{id}/location should return 404 when not found")
    void testGetDriverLocationNotFound() throws Exception {
        when(locationService.getDriverLocation("driver-unknown"))
            .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/locations/drivers/driver-unknown/location"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /drivers/{id}/location-history should return history")
    void testGetLocationHistory() throws Exception {
        List<LocationResponse> history = List.of(
            LocationResponse.builder()
                .driverId("driver-123")
                .latitude(40.7128)
                .longitude(-74.0060)
                .timestamp(Instant.now())
                .build()
        );

        when(locationService.getLocationHistory("driver-123"))
            .thenReturn(history);

        mockMvc.perform(get("/api/v1/locations/drivers/driver-123/location-history"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    @DisplayName("GET /stats should return batch statistics")
    void testGetStats() throws Exception {
        mockMvc.perform(get("/api/v1/locations/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.processedCount").exists())
            .andExpect(jsonPath("$.failedCount").exists());
    }
}
