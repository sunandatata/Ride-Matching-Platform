package com.rideshare.driver.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Document entity for storing driver documents like licenses, inspections, etc.
 * Documents are stored as URLs pointing to external storage (S3, GCS, etc).
 */
@Entity
@Table(name = "driver_documents", indexes = {
    @Index(name = "idx_documents_driver_id", columnList = "driver_id"),
    @Index(name = "idx_documents_type", columnList = "document_type")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID documentId;

    @Column(nullable = false, columnDefinition = "UUID")
    private UUID driverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentType documentType;

    @Column(nullable = false, length = 500)
    private String documentUrl;

    @Column
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.PENDING;

    @Column(length = 1000)
    private String rejectionReason;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum DocumentType {
        LICENSE, VEHICLE_REGISTRATION, INSURANCE, INSPECTION, BACKGROUND_CHECK
    }

    public enum DocumentStatus {
        PENDING, APPROVED, REJECTED, EXPIRED
    }

    /**
     * Check if document is valid and not expired.
     *
     * @return true if document is approved and not expired
     */
    public boolean isValid() {
        return status == DocumentStatus.APPROVED &&
               (expiryDate == null || !expiryDate.isBefore(LocalDate.now()));
    }
}
