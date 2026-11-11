CREATE TABLE agronomic_profiles (
    id BIGSERIAL PRIMARY KEY,
    crop_area_id BIGINT NOT NULL REFERENCES crop_areas(id),
    revision BIGINT NOT NULL CHECK(revision>0),
    soil_texture VARCHAR(100) NOT NULL,
    field_capacity_percent DOUBLE PRECISION NOT NULL,
    wilting_point_percent DOUBLE PRECISION NOT NULL,
    root_depth_centimeters DOUBLE PRECISION,
    sensor_depth_centimeters DOUBLE PRECISION,
    source VARCHAR(500) NOT NULL,
    demo BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE(crop_area_id,revision),
    CHECK(wilting_point_percent>=0 AND field_capacity_percent<=100 AND field_capacity_percent>wilting_point_percent),
    CHECK(root_depth_centimeters>0),
    CHECK(sensor_depth_centimeters>0)
);
