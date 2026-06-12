package com.rideshare.driver.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * DTO for uploading or updating driver documents.
 */
public record DocumentUploadRequest(
    @NotNull(message = "Document type is required")
    String documentType,

    @NotBlank(message = "Document URL is required")
    String documentUrl,

    @FutureOrPresent(message = "Expiry date must be in the future")
    LocalDate expiryDate
) {
    /**
     * Validate that document type is supported.
     *
     * @return true if valid
     */
    public boolean isValidDocumentType() {
        return "LICENSE".equals(documentType) ||
               "VEHICLE_REGISTRATION".equals(documentType) ||
               "INSURANCE".equals(documentType) ||
               "INSPECTION".equals(documentType) ||
               "BACKGROUND_CHECK".equals(documentType);
    }
}
