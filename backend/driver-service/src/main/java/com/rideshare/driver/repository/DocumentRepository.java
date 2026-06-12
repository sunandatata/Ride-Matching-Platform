package com.rideshare.driver.repository;

import com.rideshare.driver.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Document entity.
 * Provides database access operations for driver documents.
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    /**
     * Find all documents for a driver.
     *
     * @param driverId the driver ID
     * @return list of documents
     */
    List<Document> findByDriverId(UUID driverId);

    /**
     * Find documents by driver and type.
     *
     * @param driverId the driver ID
     * @param documentType the document type
     * @return list of documents
     */
    List<Document> findByDriverIdAndDocumentType(UUID driverId, Document.DocumentType documentType);

    /**
     * Find the latest document of a type for a driver.
     *
     * @param driverId the driver ID
     * @param documentType the document type
     * @return the document if found
     */
    @Query("SELECT d FROM Document d WHERE d.driverId = :driverId AND d.documentType = :documentType ORDER BY d.createdAt DESC LIMIT 1")
    Optional<Document> findLatestByDriverIdAndType(@Param("driverId") UUID driverId, @Param("documentType") Document.DocumentType documentType);

    /**
     * Find all documents by status.
     *
     * @param status the document status
     * @return list of documents
     */
    List<Document> findByStatus(Document.DocumentStatus status);

    /**
     * Find expired documents.
     *
     * @return list of expired documents
     */
    @Query("SELECT d FROM Document d WHERE d.expiryDate IS NOT NULL AND d.expiryDate < :today AND d.status != 'EXPIRED'")
    List<Document> findExpiredDocuments(@Param("today") LocalDate today);

    /**
     * Find documents expiring soon.
     *
     * @param startDate start of expiry period
     * @param endDate end of expiry period
     * @return list of documents
     */
    @Query("SELECT d FROM Document d WHERE d.expiryDate IS NOT NULL AND d.expiryDate BETWEEN :startDate AND :endDate AND d.status = 'APPROVED'")
    List<Document> findExpiringDocuments(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
