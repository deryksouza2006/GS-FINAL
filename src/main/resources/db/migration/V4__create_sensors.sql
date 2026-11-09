CREATE TABLE sensors (
    id BIGSERIAL PRIMARY KEY,
    crop_area_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    device_code VARCHAR(80) NOT NULL,
    sensor_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    installation_date DATE NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_sensors_crop_area FOREIGN KEY (crop_area_id) REFERENCES crop_areas(id) ON DELETE RESTRICT,
    CONSTRAINT ck_sensors_name CHECK (char_length(btrim(name)) BETWEEN 3 AND 100),
    CONSTRAINT ck_sensors_device_code CHECK (char_length(btrim(device_code)) BETWEEN 3 AND 80),
    CONSTRAINT ck_sensors_type CHECK (sensor_type IN ('SOIL_MOISTURE', 'TEMPERATURE', 'HUMIDITY', 'WEATHER_STATION')),
    CONSTRAINT ck_sensors_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'FAULT'))
);

CREATE INDEX idx_sensors_crop_area_id ON sensors(crop_area_id);
CREATE UNIQUE INDEX ux_sensors_device_code_upper ON sensors(upper(device_code));
