package com.rideshare.driver.controller;

import com.rideshare.driver.dto.*;
import com.rideshare.driver.service.DriverService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DriverController.class)
@DisplayName("DriverController Tests")
class DriverControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DriverService driverService;

    private UUID testDriverId;
    private DriverResponse testDriverResponse;

    @BeforeEach
    void setUp() {
        testDriverId = UUID.randomUUID();
        testDriverResponse = new DriverResponse(
            testDriverId,
            "+1234567890",
            "test@example.com",
            "John",
            "Doe",
            null,
            LocalDate.of(1990, 1, 1),
            "ACTIVE",
            "DL123456",
            "CA",
            LocalDate.now().plusYears(5),
            true,
            "APPROVED",
            LocalDate.now().plusYears(3),
            UUID.randomUUID(),
            "Toyota",
            "Camry",
            2023,
            "Black",
            "ABC123",
            4,
            "ECONOMY",
            "APPROVED",
            LocalDate.now().minusDays(30),
            "ONLINE",
            LocalDateTime.now(),
            new BigDecimal("4.5"),
            100,
            new BigDecimal("5000.00"),
            new BigDecimal("95.00"),
            new BigDecimal("2.00"),
            LocalDateTime.now().minusDays(30),
            LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("POST /drivers - should_register_driver_successfully")
    void testRegisterDriver_Success() throws Exception {
        // Arrange
        DriverRegistrationRequest request = new DriverRegistrationRequest(
            "+1234567890",
            "driver@test.com",
            "John",
            "Doe",
            LocalDate.of(2000, 1, 1),
            "DL123456",
            "CA",
            LocalDate.now().plusYears(5),
            "Toyota",
            "Camry",
            2023,
            "Black",
            "ABC123",
            4
        );

        when(driverService.registerDriver(any())).thenReturn(testDriverResponse);

        // Act & Assert
        mockMvc.perform(post("/drivers")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.firstName").value("John"))
            .andExpect(jsonPath("$.data.lastName").value("Doe"));

        verify(driverService).registerDriver(any());
    }

    @Test
    @DisplayName("GET /drivers/{id} - should_get_driver_successfully")
    void testGetDriver_Success() throws Exception {
        // Arrange
        when(driverService.getDriver(testDriverId)).thenReturn(testDriverResponse);

        // Act & Assert
        mockMvc.perform(get("/drivers/" + testDriverId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.firstName").value("John"))
            .andExpect(jsonPath("$.data.driverId").value(testDriverId.toString()));

        verify(driverService).getDriver(testDriverId);
    }

    @Test
    @DisplayName("GET /drivers/{id} - should_return_404_when_driver_not_found")
    void testGetDriver_NotFound() throws Exception {
        // Arrange
        when(driverService.getDriver(testDriverId))
            .thenThrow(new java.util.NoSuchElementException("Driver not found"));

        // Act & Assert
        mockMvc.perform(get("/drivers/" + testDriverId))
            .andExpect(status().isNotFound());

        verify(driverService).getDriver(testDriverId);
    }

    @Test
    @DisplayName("PUT /drivers/{id} - should_update_driver_successfully")
    void testUpdateDriver_Success() throws Exception {
        // Arrange
        DriverUpdateRequest request = new DriverUpdateRequest("Jane", "Smith", "jane@test.com", null);

        when(driverService.updateDriver(eq(testDriverId), any(), any(), any(), any()))
            .thenReturn(testDriverResponse);

        // Act & Assert
        mockMvc.perform(put("/drivers/" + testDriverId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(driverService).updateDriver(eq(testDriverId), any(), any(), any(), any());
    }

    @Test
    @DisplayName("PUT /drivers/{id}/vehicle - should_update_vehicle_successfully")
    void testUpdateVehicle_Success() throws Exception {
        // Arrange
        VehicleUpdateRequest request = new VehicleUpdateRequest(
            "Honda",
            "Civic",
            2022,
            "Silver",
            "XYZ789",
            5,
            "PREMIUM"
        );

        when(driverService.updateVehicle(eq(testDriverId), any())).thenReturn(testDriverResponse);

        // Act & Assert
        mockMvc.perform(put("/drivers/" + testDriverId + "/vehicle")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(driverService).updateVehicle(eq(testDriverId), any());
    }

    @Test
    @DisplayName("PUT /drivers/{id}/availability-status - should_update_status_successfully")
    void testUpdateAvailabilityStatus_Success() throws Exception {
        // Arrange
        AvailabilityStatusUpdateRequest request = new AvailabilityStatusUpdateRequest("ONLINE");

        when(driverService.updateAvailabilityStatus(eq(testDriverId), any()))
            .thenReturn(testDriverResponse);

        // Act & Assert
        mockMvc.perform(put("/drivers/" + testDriverId + "/availability-status")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(driverService).updateAvailabilityStatus(eq(testDriverId), any());
    }

    @Test
    @DisplayName("PUT /drivers/{id}/last-activity - should_update_activity_successfully")
    void testUpdateLastActivity_Success() throws Exception {
        // Arrange
        doNothing().when(driverService).updateLastActivity(testDriverId);

        // Act & Assert
        mockMvc.perform(put("/drivers/" + testDriverId + "/last-activity"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(driverService).updateLastActivity(testDriverId);
    }

    @Test
    @DisplayName("POST /drivers/{id}/documents - should_upload_document_successfully")
    void testUploadDocument_Success() throws Exception {
        // Arrange
        DocumentUploadRequest request = new DocumentUploadRequest(
            "LICENSE",
            "https://s3.example.com/license.pdf",
            LocalDate.now().plusYears(5)
        );

        UUID documentId = UUID.randomUUID();
        when(driverService.uploadDocument(eq(testDriverId), any())).thenReturn(documentId);

        // Act & Assert
        mockMvc.perform(post("/drivers/" + testDriverId + "/documents")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true));

        verify(driverService).uploadDocument(eq(testDriverId), any());
    }

    @Test
    @DisplayName("GET /drivers/{id}/documents - should_get_documents_successfully")
    void testGetDocuments_Success() throws Exception {
        // Arrange
        when(driverService.getDriverDocuments(testDriverId))
            .thenReturn(Arrays.asList());

        // Act & Assert
        mockMvc.perform(get("/drivers/" + testDriverId + "/documents"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data", hasSize(0)));

        verify(driverService).getDriverDocuments(testDriverId);
    }

    @Test
    @DisplayName("GET /drivers/{id}/stats - should_get_stats_successfully")
    void testGetStats_Success() throws Exception {
        // Arrange
        DriverStatsResponse statsResponse = new DriverStatsResponse(
            testDriverId.toString(),
            new BigDecimal("4.5"),
            100,
            new BigDecimal("95.00"),
            new BigDecimal("2.00"),
            new BigDecimal("5000.00")
        );

        when(driverService.getDriverStats(testDriverId)).thenReturn(statsResponse);

        // Act & Assert
        mockMvc.perform(get("/drivers/" + testDriverId + "/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.averageRating").value(4.5))
            .andExpect(jsonPath("$.data.totalRides").value(100));

        verify(driverService).getDriverStats(testDriverId);
    }

    @Test
    @DisplayName("POST /drivers/batch - should_get_batch_drivers_successfully")
    void testGetBatchDrivers_Success() throws Exception {
        // Arrange
        List<UUID> driverIds = Arrays.asList(testDriverId, UUID.randomUUID());
        BatchDriverLookupRequest request = new BatchDriverLookupRequest(driverIds);

        when(driverService.getBatchDrivers(driverIds))
            .thenReturn(Arrays.asList(testDriverResponse));

        // Act & Assert
        mockMvc.perform(post("/drivers/batch")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data", hasSize(1)));

        verify(driverService).getBatchDrivers(driverIds);
    }

    @Test
    @DisplayName("POST /drivers - should_return_400_on_invalid_phone_number")
    void testRegisterDriver_InvalidPhone() throws Exception {
        // Arrange
        DriverRegistrationRequest request = new DriverRegistrationRequest(
            "invalid",
            "driver@test.com",
            "John",
            "Doe",
            LocalDate.of(2000, 1, 1),
            "DL123456",
            "CA",
            LocalDate.now().plusYears(5),
            "Toyota",
            "Camry",
            2023,
            "Black",
            "ABC123",
            4
        );

        // Act & Assert
        mockMvc.perform(post("/drivers")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());

        verify(driverService, never()).registerDriver(any());
    }
}
