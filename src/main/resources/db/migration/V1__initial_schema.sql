CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_users_role CHECK (role IN ('PRODUCER', 'TECHNICIAN', 'ADMIN'))
);

CREATE TABLE farms (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(120) NOT NULL,
    responsible_name VARCHAR(100) NOT NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(2) NOT NULL,
    country VARCHAR(80) NOT NULL,
    latitude NUMERIC(9, 6),
    longitude NUMERIC(9, 6),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_farms_owner_user_id ON farms(owner_user_id);

CREATE TABLE crop_areas (
    id BIGSERIAL PRIMARY KEY,
    farm_id BIGINT NOT NULL REFERENCES farms(id),
    name VARCHAR(120) NOT NULL,
    crop_type VARCHAR(80) NOT NULL,
    area_size NUMERIC(12, 2) NOT NULL,
    area_unit VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    latitude NUMERIC(9, 6),
    longitude NUMERIC(9, 6),
    description VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_crop_areas_area_size CHECK (area_size > 0),
    CONSTRAINT ck_crop_areas_area_unit CHECK (area_unit IN ('HECTARE', 'SQUARE_METER')),
    CONSTRAINT ck_crop_areas_status CHECK (status IN ('NORMAL', 'ATTENTION', 'CRITICAL'))
);

CREATE INDEX idx_crop_areas_farm_id ON crop_areas(farm_id);
