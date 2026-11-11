CREATE TABLE field_activities (
    id BIGSERIAL PRIMARY KEY,
    crop_area_id BIGINT NOT NULL REFERENCES crop_areas(id),
    author_id BIGINT NOT NULL REFERENCES users(id),
    type VARCHAR(30) NOT NULL CHECK (type IN ('IRRIGATION','FERTILIZATION','PLANTING','HARVEST')),
    performed_at TIMESTAMPTZ NOT NULL,
    description VARCHAR(2000),
    quantity NUMERIC(14,3),
    unit VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CHECK ((quantity IS NULL AND unit IS NULL) OR (quantity > 0 AND unit IS NOT NULL AND length(trim(unit)) > 0))
);
CREATE INDEX idx_field_activities_area_time ON field_activities(crop_area_id,performed_at DESC,id DESC);
