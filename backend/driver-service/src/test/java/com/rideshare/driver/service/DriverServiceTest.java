package com.rideshare.driver.service;

import com.rideshare.driver.dto.*;
import com.rideshare.driver.entity.Document;
import com.rideshare.driver.entity.Driver;
import com.rideshare.driver.repository.DriverRepository;
import com.rideshare.driver.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DriverService Tests")
class DriverServiceTest {

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private DriverService driverService;

    private UUID testDriverId;
    private Driver testDriver;

    @BeforeEach
    void setUp() {
        driverService = new DriverService(driverRepository, documentRepository, redisTemplate);
        testDriverId = UUID.randomUUID();
        testDriver = createTestDriver();

        // Setup Redis mock
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("should_register_driver_successfully_when_all_requirements_met")
    void testRegisterDriver_Success() {
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

        when(driverRepository.findByPhoneNumber(request.phoneNumber())).thenReturn(Optional.empty());
        when(driverRepository.findByLicenseNumber(request.licenseNumber())).thenReturn(Optional.empty());
        when(driverRepository.findByVehicleLicensePlate(request.vehicleLicensePlate())).thenReturn(Optional.empty());
        when(driverRepository.save(any(Driver.class))).thenReturn(testDriver);

        // Act
        DriverResponse response = driverService.registerDriver(request);

        // Assert
        assertNotNull(response);
        assertEquals(request.firstName(), response.firstName());
        assertEquals(request.lastName(), response.lastName());
        verify(driverRepository).save(any(Driver.class));
    }

    @Test
    @DisplayName("should_fail_registration_when_phone_number_already_exists")
    void testRegisterDriver_PhoneNumberExists() {
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

        when(driverRepository.findByPhoneNumber(request.phoneNumber()))
            .thenReturn(Optional.of(testDriver));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> driverService.registerDriver(request));
    }

    @Test
    @DisplayName("should_fail_registration_when_driver_under_18_years_old")
    void testRegisterDriver_UnderAgeDriver() {
        // Arrange
        DriverRegistrationRequest request = new DriverRegistrationRequest(
            "+1234567890",
            "driver@test.com",
            "John",
            "Doe",
            LocalDate.now().minusYears(17),
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

        when(driverRepository.findByPhoneNumber(request.phoneNumber())).thenReturn(Optional.empty());
        when(driverRepository.findByLicenseNumber(request.licenseNumber())).thenReturn(Optional.empty());
        when(driverRepository.findByVehicleLicensePlate(request.vehicleLicensePlate())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> driverService.registerDriver(request));
    }

    @Test
    @DisplayName("should_get_driver_from_cache_when_available")
    void testGetDriver_FromCache() {
        // Arrange
        DriverResponse cachedResponse = DriverResponse.fromEntity(testDriver);
        when(valueOperations.get("driver:" + testDriverId)).thenReturn(cachedResponse);

        // Act
        DriverResponse response = driverService.getDriver(testDriverId);

        // Assert
        assertNotNull(response);
        assertEquals(testDriver.getFirstName(), response.firstName());
        verify(driverRepository, never()).findById(any());
    }

    @Test
    @DisplayName("should_get_driver_from_database_when_cache_miss")
    void testGetDriver_FromDatabase() {
        // Arrange
        when(valueOperations.get("driver:" + testDriverId)).thenReturn(null);
        when(driverRepository.findById(testDriverId)).thenReturn(Optional.of(testDriver));

        // Act
        DriverResponse response = driverService.getDriver(testDriverId);

        // Assert
        assertNotNull(response);
        assertEquals(testDriver.getFirstName(), response.firstName());
        verify(driverRepository).findById(testDriverId);
        verify(valueOperations).set(eq("driver:" + testDriverId), any(), eq(30L), eq(java.util.concurrent.TimeUnit.MINUTES));
    }

    @Test
    @DisplayName("should_throw_exception_when_driver_not_found")
    void testGetDriver_NotFound() {
        // Arrange
        when(valueOperations.get("driver:" + testDriverId)).thenReturn(null);
        when(driverRepository.findById(testDriverId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(NoSuchElementException.class, () -> driverService.getDriver(testDriverId));
    }

    @Test
    @DisplayName("should_update_driver_profile_successfully")
    void testUpdateDriver_Success() {
        // Arrange
        String newFirstName = "Jane";
        String newLastName = "Smith";
        String newEmail = "jane@test.com";

        when(driverRepository.findById(testDriverId)).thenReturn(Optional.of(testDriver));
        when(driverRepository.save(any(Driver.class))).thenReturn(testDriver);

        // Act
        DriverResponse response = driverService.updateDriver(
            testDriverId,
            newFirstName,
            newLastName,
            newEmail,
            null
        );

        // Assert
        assertNotNull(response);
        verify(driverRepository).save(any(Driver.class));
    }

    @Test
    @DisplayName("should_update_vehicle_successfully_when_license_plate_unique")
    void testUpdateVehicle_Success() {
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

        when(driverRepository.findById(testDriverId)).thenReturn(Optional.of(testDriver));
        when(driverRepository.findByVehicleLicensePlate(request.vehicleLicensePlate()))
            .thenReturn(Optional.empty());
        when(driverRepository.save(any(Driver.class))).thenReturn(testDriver);

        // Act
        DriverResponse response = driverService.updateVehicle(testDriverId, request);

        // Assert
        assertNotNull(response);
        verify(driverRepository).save(any(Driver.class));
    }

    @Test
    @DisplayName("should_fail_update_vehicle_when_license_plate_already_registered")
    void testUpdateVehicle_DuplicatePlate() {
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

        when(driverRepository.findById(testDriverId)).thenReturn(Optional.of(testDriver));
        when(driverRepository.findByVehicleLicensePlate(request.vehicleLicensePlate()))
            .thenReturn(Optional.of(createTestDriver()));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> driverService.updateVehicle(testDriverId, request));
    }

    @Test
    @DisplayName("should_update_availability_status_to_online_when_driver_eligible")
    void testUpdateAvailabilityStatus_ToOnline_Eligible() {
        // Arrange
        Driver eligibleDriver = createEligibleDriver();
        AvailabilityStatusUpdateRequest request = new AvailabilityStatusUpdateRequest("ONLINE");

        when(driverRepository.findById(testDriverId)).thenReturn(Optional.of(eligibleDriver));
        when(driverRepository.save(any(Driver.class))).thenReturn(eligibleDriver);

        // Act
        DriverResponse response = driverService.updateAvailabilityStatus(testDriverId, request);

        // Assert
        assertNotNull(response);
        verify(driverRepository).save(any(Driver.class));
    }

    @Test
    @DisplayName("should_fail_to_go_online_when_driver_not_eligible")
    void testUpdateAvailabilityStatus_ToOnline_NotEligible() {
        // Arrange
        AvailabilityStatusUpdateRequest request = new AvailabilityStatusUpdateRequest("ONLINE");

        when(driverRepository.findById(testDriverId)).thenReturn(Optional.of(testDriver));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> driverService.updateAvailabilityStatus(testDriverId, request));
    }

    @Test
    @DisplayName("should_update_last_activity_timestamp")
    void testUpdateLastActivity_Success() {
        // Arrange
        when(driverRepository.findById(testDriverId)).thenReturn(Optional.of(testDriver));
        when(driverRepository.save(any(Driver.class))).thenReturn(testDriver);

        // Act
        driverService.updateLastActivity(testDriverId);

        // Assert
        verify(driverRepository).save(any(Driver.class));
    }

    @Test
    @DisplayName("should_get_batch_of_drivers_successfully")
    void testGetBatchDrivers_Success() {
        // Arrange
        List<UUID> driverIds = Arrays.asList(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        List<Driver> drivers = Arrays.asList(testDriver, testDriver, testDriver);

        when(driverRepository.findAllById(driverIds)).thenReturn(drivers);

        // Act
        List<DriverResponse> responses = driverService.getBatchDrivers(driverIds);

        // Assert
        assertNotNull(responses);
        assertEquals(3, responses.size());
        verify(driverRepository).findAllById(driverIds);
    }

    @Test
    @DisplayName("should_upload_document_successfully")
    void testUploadDocument_Success() {
        // Arrange
        DocumentUploadRequest request = new DocumentUploadRequest(
            "LICENSE",
            "https://s3.example.com/license.pdf",
            LocalDate.now().plusYears(5)
        );

        when(driverRepository.existsById(testDriverId)).thenReturn(true);
        when(documentRepository.save(any(Document.class))).thenReturn(
            Document.builder()
                .documentId(UUID.randomUUID())
                .driverId(testDriverId)
                .documentType(Document.DocumentType.LICENSE)
                .documentUrl(request.documentUrl())
                .expiryDate(request.expiryDate())
                .status(Document.DocumentStatus.PENDING)
                .build()
        );

        // Act
        UUID documentId = driverService.uploadDocument(testDriverId, request);

        // Assert
        assertNotNull(documentId);
        verify(documentRepository).save(any(Document.class));
    }

    @Test
    @DisplayName("should_get_driver_documents_successfully")
    void testGetDriverDocuments_Success() {
        // Arrange
        List<Document> documents = Arrays.asList(
            Document.builder()
                .documentId(UUID.randomUUID())
                .driverId(testDriverId)
                .documentType(Document.DocumentType.LICENSE)
                .documentUrl("https://s3.example.com/license.pdf")
                .status(Document.DocumentStatus.PENDING)
                .build()
        );

        when(driverRepository.existsById(testDriverId)).thenReturn(true);
        when(documentRepository.findByDriverId(testDriverId)).thenReturn(documents);

        // Act
        List<Document> result = driverService.getDriverDocuments(testDriverId);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(documentRepository).findByDriverId(testDriverId);
    }

    @Test
    @DisplayName("should_get_driver_stats_from_cache_when_available")
    void testGetDriverStats_FromCache() {
        // Arrange
        DriverStatsResponse cachedStats = new DriverStatsResponse(
            testDriverId.toString(),
            new BigDecimal("4.5"),
            100,
            new BigDecimal("95.00"),
            new BigDecimal("2.00"),
            new BigDecimal("5000.00")
        );

        when(valueOperations.get("driver_stats:" + testDriverId)).thenReturn(cachedStats);

        // Act
        DriverStatsResponse stats = driverService.getDriverStats(testDriverId);

        // Assert
        assertNotNull(stats);
        assertEquals(cachedStats.averageRating(), stats.averageRating());
        verify(driverRepository, never()).findById(any());
    }

    @Test
    @DisplayName("should_update_driver_metrics_successfully")
    void testUpdateDriverMetrics_Success() {
        // Arrange
        BigDecimal rating = new BigDecimal("4.8");
        BigDecimal earnings = new BigDecimal("25.50");

        when(driverRepository.findById(testDriverId)).thenReturn(Optional.of(testDriver));
        when(driverRepository.save(any(Driver.class))).thenReturn(testDriver);

        // Act
        driverService.updateDriverMetrics(testDriverId, rating, earnings, true);

        // Assert
        verify(driverRepository).save(any(Driver.class));
        verify(valueOperations).delete("driver_stats:" + testDriverId);
    }

    @Test
    @DisplayName("should_validate_license_expiry")
    void testDriverValidation_LicenseExpiry() {
        // Arrange & Act
        Driver expiredLicense = createTestDriver();
        expiredLicense.setLicenseExpiryDate(LocalDate.now().minusDays(1));

        // Assert
        assertFalse(expiredLicense.isLicenseValid());
    }

    @Test
    @DisplayName("should_validate_background_check")
    void testDriverValidation_BackgroundCheck() {
        // Arrange & Act
        Driver validBackgroundCheck = createEligibleDriver();

        // Assert
        assertTrue(validBackgroundCheck.isBackgroundCheckValid());
    }

    @Test
    @DisplayName("should_validate_driver_eligibility")
    void testDriverValidation_Eligibility() {
        // Arrange & Act
        Driver eligibleDriver = createEligibleDriver();
        Driver ineligibleDriver = createTestDriver();

        // Assert
        assertTrue(eligibleDriver.isEligibleForRides());
        assertFalse(ineligibleDriver.isEligibleForRides());
    }

    // Helper methods
    private Driver createTestDriver() {
        return Driver.builder()
            .driverId(testDriverId)
            .phoneNumber("+1234567890")
            .email("test@example.com")
            .firstName("John")
            .lastName("Doe")
            .dateOfBirth(LocalDate.of(1990, 1, 1))
            .status(Driver.DriverStatus.ACTIVE)
            .licenseNumber("DL123456")
            .licenseState("CA")
            .licenseExpiryDate(LocalDate.now().minusDays(1))
            .licenseVerified(false)
            .backgroundCheckStatus(Driver.BackgroundCheckStatus.PENDING)
            .vehicleId(UUID.randomUUID())
            .vehicleMake("Toyota")
            .vehicleModel("Camry")
            .vehicleYear(2023)
            .vehicleColor("Black")
            .vehicleLicensePlate("ABC123")
            .vehicleCapacity(4)
            .vehicleType(Driver.VehicleType.ECONOMY)
            .vehicleInspectionStatus(Driver.VehicleInspectionStatus.PENDING)
            .availabilityStatus(Driver.AvailabilityStatus.OFFLINE)
            .averageRating(new BigDecimal("0.00"))
            .totalRides(0)
            .totalEarnings(new BigDecimal("0.00"))
            .acceptanceRate(new BigDecimal("100.00"))
            .cancellationRate(new BigDecimal("0.00"))
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private Driver createEligibleDriver() {
        return Driver.builder()
            .driverId(testDriverId)
            .phoneNumber("+1234567890")
            .email("test@example.com")
            .firstName("John")
            .lastName("Doe")
            .dateOfBirth(LocalDate.of(1990, 1, 1))
            .status(Driver.DriverStatus.ACTIVE)
            .licenseNumber("DL123456")
            .licenseState("CA")
            .licenseExpiryDate(LocalDate.now().plusYears(5))
            .licenseVerified(true)
            .backgroundCheckStatus(Driver.BackgroundCheckStatus.APPROVED)
            .backgroundCheckExpiresDate(LocalDate.now().plusYears(3))
            .vehicleId(UUID.randomUUID())
            .vehicleMake("Toyota")
            .vehicleModel("Camry")
            .vehicleYear(2023)
            .vehicleColor("Black")
            .vehicleLicensePlate("ABC123")
            .vehicleCapacity(4)
            .vehicleType(Driver.VehicleType.ECONOMY)
            .vehicleInspectionStatus(Driver.VehicleInspectionStatus.APPROVED)
            .vehicleInspectionDate(LocalDate.now().minusDays(30))
            .availabilityStatus(Driver.AvailabilityStatus.OFFLINE)
            .averageRating(new BigDecimal("4.5"))
            .totalRides(100)
            .totalEarnings(new BigDecimal("5000.00"))
            .acceptanceRate(new BigDecimal("95.00"))
            .cancellationRate(new BigDecimal("2.00"))
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }
}
