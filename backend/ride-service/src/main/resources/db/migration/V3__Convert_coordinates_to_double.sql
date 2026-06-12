-- Align coordinate storage with the Ride entity's Double fields.
ALTER TABLE rides ALTER COLUMN pickup_latitude TYPE DOUBLE PRECISION USING pickup_latitude::DOUBLE PRECISION;
ALTER TABLE rides ALTER COLUMN pickup_longitude TYPE DOUBLE PRECISION USING pickup_longitude::DOUBLE PRECISION;
ALTER TABLE rides ALTER COLUMN dropoff_latitude TYPE DOUBLE PRECISION USING dropoff_latitude::DOUBLE PRECISION;
ALTER TABLE rides ALTER COLUMN dropoff_longitude TYPE DOUBLE PRECISION USING dropoff_longitude::DOUBLE PRECISION;
