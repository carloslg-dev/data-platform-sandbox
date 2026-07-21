-- Initialize antennas table
CREATE TABLE IF NOT EXISTS antennas (
    id VARCHAR(50) PRIMARY KEY,
    location VARCHAR(255) NOT NULL,
    type VARCHAR(10) NOT NULL, -- '3G', '4G', '5G'
    theoretical_capacity INT NOT NULL, -- Mbps
    status VARCHAR(20) NOT NULL -- 'ACTIVE', 'INACTIVE', 'MAINTENANCE'
);

-- Seed antennas table with initial data for local development/testing
INSERT INTO antennas (id, location, type, theoretical_capacity, status) VALUES
('antenna-101', 'Madrid', '5G', 1000, 'ACTIVE'),
('antenna-102', 'Barcelona', '4G', 500, 'ACTIVE')
ON CONFLICT (id) DO UPDATE SET
    location = EXCLUDED.location,
    type = EXCLUDED.type,
    theoretical_capacity = EXCLUDED.theoretical_capacity,
    status = EXCLUDED.status;
