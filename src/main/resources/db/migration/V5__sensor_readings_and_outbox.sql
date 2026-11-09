CREATE TABLE sensor_readings (
    id BIGSERIAL PRIMARY KEY,
    sensor_id BIGINT NOT NULL,
    temperature_celsius NUMERIC(6, 3),
    air_humidity_percent NUMERIC(6, 3),
    soil_moisture_percent NUMERIC(6, 3),
    battery_percent NUMERIC(6, 3),
    measured_at TIMESTAMPTZ NOT NULL,
    source VARCHAR(20) NOT NULL,
    analysis_status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_sensor_readings_sensor FOREIGN KEY (sensor_id) REFERENCES sensors(id) ON DELETE RESTRICT,
    CONSTRAINT ck_readings_temperature CHECK (temperature_celsius BETWEEN -50 AND 70),
    CONSTRAINT ck_readings_air_humidity CHECK (air_humidity_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_readings_soil_moisture CHECK (soil_moisture_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_readings_battery CHECK (battery_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_readings_measurement CHECK (temperature_celsius IS NOT NULL OR air_humidity_percent IS NOT NULL
        OR soil_moisture_percent IS NOT NULL OR battery_percent IS NOT NULL),
    CONSTRAINT ck_readings_source CHECK (source IN ('MANUAL', 'DEVICE')),
    CONSTRAINT ck_readings_analysis_status CHECK (analysis_status IN ('PENDING_PUBLICATION', 'QUEUED_FOR_ANALYSIS', 'ANALYZED', 'FAILED'))
);
CREATE INDEX idx_readings_sensor_measured ON sensor_readings(sensor_id, measured_at DESC, id DESC);
CREATE INDEX idx_readings_measured_at ON sensor_readings(measured_at DESC, id DESC);
CREATE INDEX idx_readings_analysis_status ON sensor_readings(analysis_status);

CREATE TABLE outbox_events (
    event_id UUID PRIMARY KEY,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(500),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT ck_outbox_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_outbox_payload CHECK (jsonb_typeof(payload) = 'object')
);
CREATE INDEX idx_outbox_pending ON outbox_events(status, next_attempt_at, created_at);
CREATE INDEX idx_outbox_created_at ON outbox_events(created_at);
CREATE INDEX idx_outbox_aggregate ON outbox_events(aggregate_type, aggregate_id);
