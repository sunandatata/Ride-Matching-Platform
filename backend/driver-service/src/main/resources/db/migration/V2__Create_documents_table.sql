-- Create driver_documents table for storing driver documents (URLs only, no binaries)
CREATE TABLE IF NOT EXISTS driver_documents (
    document_id UUID PRIMARY KEY,
    driver_id UUID NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    document_url VARCHAR(500) NOT NULL,
    expiry_date DATE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    rejection_reason VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (driver_id) REFERENCES drivers(driver_id) ON DELETE CASCADE
);

-- Create indexes for efficient queries
CREATE INDEX idx_documents_driver_id ON driver_documents(driver_id);
CREATE INDEX idx_documents_type ON driver_documents(document_type);
CREATE INDEX idx_documents_driver_type ON driver_documents(driver_id, document_type);
CREATE INDEX idx_documents_status ON driver_documents(status);
CREATE INDEX idx_documents_expiry ON driver_documents(expiry_date) WHERE expiry_date IS NOT NULL;
