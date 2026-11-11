CREATE TABLE risk_jobs (
 id BIGSERIAL PRIMARY KEY,
 crop_area_id BIGINT NOT NULL REFERENCES crop_areas(id),
 owner_id BIGINT NOT NULL REFERENCES users(id),
 profile_id BIGINT REFERENCES agronomic_profiles(id),
 field_capacity DOUBLE PRECISION,
 wilting_point DOUBLE PRECISION,
 demo BOOLEAN NOT NULL,
 status VARCHAR(40) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL,
 completed_at TIMESTAMPTZ,
 measurements JSONB NOT NULL,
 result JSONB,
 rules_version VARCHAR(30) NOT NULL,
 alert_status VARCHAR(30),
 acknowledged_at TIMESTAMPTZ,
 resolved_at TIMESTAMPTZ
);
CREATE INDEX idx_risk_jobs_owner ON risk_jobs(owner_id,crop_area_id,id DESC);
