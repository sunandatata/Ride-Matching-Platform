package com.rideshare.ride.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rideshare.ride.dto.*;
import com.rideshare.ride.entity.RideStatus;
import com.rideshare.ride.event.RideEventPublisher;
import com.rideshare.ride.service.RideService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for RideController.
 * Tests all API endpoints with HTTP request/response validation.
 */
@WebMvcTest(RideController.class)
@DisplayName("RideController Integration Tests")
class RideControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RideService rideService;

    @MockBean
    private RideEventPublisher rideEventPublisher;

    private RideResponse testRideResponse;

    @BeforeEach
    void setUp() {
        testRideResponse = RideResponse.builder()
                .id("RIDE-123")
                .riderId("RIDER-123")
                .driverId("DRIVER-456")
                .status(RideStatus.REQUESTED)
                .pickupLatitude(40.7128)
                .pickupLongitude(-74.0060)
                .pickupAddress("123 Main St, NYC")
                .dropoffLatitude(40.7580)
                .dropoffLongitude(-73.9855)
                .dropoffAddress("456 Park Ave, NYC")
                .passengerCount(2)
                .estimatedFare(new BigDecimal("25.50"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/rides - Create ride successfully")
    void testCreateRide_Success() throws Exception {
        // Arrange
        CreateRideRequest request = CreateRideRequest.builder()
                .riderId("RIDER-123")
                .pickupLatitude(40.7128)
                .pickupLongitude(-74.0060)
                .pickupAddress("123 Main St, NYC")
                .dropoffLatitude(40.7580)
                .dropoffLongitude(-73.9855)
                .dropoffAddress("456 Park Ave, NYC")
                .passengerCount(2)
                .build();

        when(rideService.createRide(org.mockito.ArgumentMatchers.any(CreateRideRequest.class))).thenReturn(testRideResponse);

        // Act & Assert
        mockMvc.perform(post("/api/v1/rides")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", equalTo("RIDE-123")))
                .andExpect(jsonPath("$.riderId", equalTo("RIDER-123")))
                .andExpect(jsonPath("$.status", equalTo("REQUESTED")))
                .andExpect(jsonPath("$.passengerCount", equalTo(2)));

        verify(rideService).createRide(org.mockito.ArgumentMatchers.any(CreateRideRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/rides - Invalid request validation")
    void testCreateRide_ValidationError() throws Exception {
        // Arrange - Missing required fields
        CreateRideRequest request = CreateRideRequest.builder()
                .pickupLatitude(40.7128)
                // Missing other required fields
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/v1/rides")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(rideService, never()).createRide(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("GET /api/v1/rides/{ride_id} - Get ride details")
    void testGetRide_Success() throws Exception {
        // Arrange
        when(rideService.getRide("RIDE-123")).thenReturn(testRideResponse);

        // Act & Assert
        mockMvc.perform(get("/api/v1/rides/RIDE-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo("RIDE-123")))
                .andExpect(jsonPath("$.riderId", equalTo("RIDER-123")))
                .andExpect(jsonPath("$.status", equalTo("REQUESTED")));

        verify(rideService).getRide("RIDE-123");
    }

    @Test
    @DisplayName("PUT /api/v1/rides/{ride_id}/driver - Assign driver")
    void testAssignDriver_Success() throws Exception {
        // Arrange
        AssignDriverRequest request = AssignDriverRequest.builder()
                .driverId("DRIVER-456")
                .estimatedFare(new BigDecimal("25.50"))
                .estimatedDurationSeconds(600)
                .driverETA(120)
                .build();

        RideResponse matchedRide = RideResponse.builder()
                .id("RIDE-123")
                .status(RideStatus.MATCHED)
                .driverId("DRIVER-456")
                .estimatedFare(new BigDecimal("25.50"))
                .build();

        when(rideService.assignDriver(eq("RIDE-123"), org.mockito.ArgumentMatchers.any(AssignDriverRequest.class)))
                .thenReturn(matchedRide);

        // Act & Assert
        mockMvc.perform(put("/api/v1/rides/RIDE-123/driver")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("MATCHED")))
                .andExpect(jsonPath("$.driverId", equalTo("DRIVER-456")));

        verify(rideService).assignDriver(eq("RIDE-123"), org.mockito.ArgumentMatchers.any(AssignDriverRequest.class));
    }

    @Test
    @DisplayName("PUT /api/v1/rides/{ride_id}/status - Update status")
    void testUpdateRideStatus_Success() throws Exception {
        // Arrange
        StatusUpdateRequest request = StatusUpdateRequest.builder()
                .status(RideStatus.ACCEPTED)
                .initiatorId("DRIVER-456")
                .initiatorType("DRIVER")
                .build();

        RideResponse acceptedRide = RideResponse.builder()
                .id("RIDE-123")
                .status(RideStatus.ACCEPTED)
                .build();

        when(rideService.updateRideStatus(eq("RIDE-123"), org.mockito.ArgumentMatchers.any(StatusUpdateRequest.class)))
                .thenReturn(acceptedRide);

        // Act & Assert
        mockMvc.perform(put("/api/v1/rides/RIDE-123/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("ACCEPTED")));

        verify(rideService).updateRideStatus(eq("RIDE-123"), org.mockito.ArgumentMatchers.any(StatusUpdateRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/rides/{ride_id}/cancel - Cancel ride")
    void testCancelRide_Success() throws Exception {
        // Arrange
        CancelRideRequest request = CancelRideRequest.builder()
                .reason("Driver not arriving")
                .initiatorId("RIDER-123")
                .initiatorType("RIDER")
                .build();

        RideResponse cancelledRide = RideResponse.builder()
                .id("RIDE-123")
                .status(RideStatus.CANCELLED)
                .cancellationReason("Driver not arriving")
                .cancellationInitiator("RIDER")
                .build();

        when(rideService.cancelRide(eq("RIDE-123"), org.mockito.ArgumentMatchers.any(CancelRideRequest.class)))
                .thenReturn(cancelledRide);

        // Act & Assert
        mockMvc.perform(post("/api/v1/rides/RIDE-123/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("CANCELLED")))
                .andExpect(jsonPath("$.cancellationReason", equalTo("Driver not arriving")));

        verify(rideService).cancelRide(eq("RIDE-123"), org.mockito.ArgumentMatchers.any(CancelRideRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/rides/{ride_id}/complete - Complete ride")
    void testCompleteRide_Success() throws Exception {
        // Arrange
        BigDecimal actualFare = new BigDecimal("28.75");

        RideResponse completedRide = RideResponse.builder()
                .id("RIDE-123")
                .status(RideStatus.COMPLETED)
                .actualFare(actualFare)
                .actualDurationSeconds(900)
                .build();

        when(rideService.completeRide("RIDE-123", actualFare))
                .thenReturn(completedRide);

        // Act & Assert
        mockMvc.perform(post("/api/v1/rides/RIDE-123/complete")
                .param("actualFare", actualFare.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("COMPLETED")))
                .andExpect(jsonPath("$.actualFare", closeTo(28.75, 0.01)));

        verify(rideService).completeRide("RIDE-123", actualFare);
    }

    @Test
    @DisplayName("POST /api/v1/rides/{ride_id}/rating - Rate ride")
    void testRateRide_Success() throws Exception {
        // Arrange
        RatingRequest request = RatingRequest.builder()
                .raterId("RIDER-123")
                .raterType("RIDER")
                .rating(5)
                .feedback("Excellent service!")
                .build();

        RideResponse ratedRide = RideResponse.builder()
                .id("RIDE-123")
                .status(RideStatus.COMPLETED)
                .driverRating(5)
                .driverFeedback("Excellent service!")
                .build();

        when(rideService.rateRide(eq("RIDE-123"), org.mockito.ArgumentMatchers.any(RatingRequest.class)))
                .thenReturn(ratedRide);

        // Act & Assert
        mockMvc.perform(post("/api/v1/rides/RIDE-123/rating")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverRating", equalTo(5)))
                .andExpect(jsonPath("$.driverFeedback", equalTo("Excellent service!")));

        verify(rideService).rateRide(eq("RIDE-123"), org.mockito.ArgumentMatchers.any(RatingRequest.class));
    }

    @Test
    @DisplayName("GET /api/v1/riders/{rider_id}/rides - Get rider rides")
    void testGetRiderRides_Success() throws Exception {
        // Arrange
        List<RideResponse> rides = new ArrayList<>();
        rides.add(testRideResponse);
        Page<RideResponse> page = new PageImpl<>(rides);

        when(rideService.getRiderRides(eq("RIDER-123"), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/v1/riders/RIDER-123/rides")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", equalTo("RIDE-123")));

        verify(rideService).getRiderRides(eq("RIDER-123"), org.mockito.ArgumentMatchers.any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/v1/drivers/{driver_id}/rides - Get driver rides")
    void testGetDriverRides_Success() throws Exception {
        // Arrange
        List<RideResponse> rides = new ArrayList<>();
        rides.add(testRideResponse);
        Page<RideResponse> page = new PageImpl<>(rides);

        when(rideService.getDriverRides(eq("DRIVER-456"), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/v1/drivers/DRIVER-456/rides")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].driverId", equalTo("DRIVER-456")));

        verify(rideService).getDriverRides(eq("DRIVER-456"), org.mockito.ArgumentMatchers.any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/v1/rides/{ride_id}/events - Get ride events")
    void testGetRideEvents_Success() throws Exception {
        // Arrange
        List<RideEventResponse> events = new ArrayList<>();
        RideEventResponse event = RideEventResponse.builder()
                .id(1L)
                .rideId("RIDE-123")
                .eventType(com.rideshare.ride.entity.RideEventType.RIDE_REQUESTED)
                .newStatus(RideStatus.REQUESTED)
                .createdAt(LocalDateTime.now())
                .build();
        events.add(event);
        Page<RideEventResponse> page = new PageImpl<>(events);

        when(rideService.getRideEvents(eq("RIDE-123"), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/v1/rides/RIDE-123/events")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].rideId", equalTo("RIDE-123")));

        verify(rideService).getRideEvents(eq("RIDE-123"), org.mockito.ArgumentMatchers.any(Pageable.class));
    }

    @Test
    @DisplayName("POST /api/v1/rides - Validate passenger count constraints")
    void testCreateRide_InvalidPassengerCount() throws Exception {
        // Arrange - passenger count exceeds max
        CreateRideRequest request = CreateRideRequest.builder()
                .riderId("RIDER-123")
                .pickupLatitude(40.7128)
                .pickupLongitude(-74.0060)
                .pickupAddress("123 Main St")
                .dropoffLatitude(40.7580)
                .dropoffLongitude(-73.9855)
                .dropoffAddress("456 Park Ave")
                .passengerCount(10) // Invalid: max is 6
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/v1/rides")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(rideService, never()).createRide(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("POST /api/v1/rides - Validate coordinates bounds")
    void testCreateRide_InvalidCoordinates() throws Exception {
        // Arrange - invalid latitude
        CreateRideRequest request = CreateRideRequest.builder()
                .riderId("RIDER-123")
                .pickupLatitude(95.0) // Invalid: max is 90
                .pickupLongitude(-74.0060)
                .pickupAddress("123 Main St")
                .dropoffLatitude(40.7580)
                .dropoffLongitude(-73.9855)
                .dropoffAddress("456 Park Ave")
                .passengerCount(2)
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/v1/rides")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(rideService, never()).createRide(org.mockito.ArgumentMatchers.any());
    }
}
