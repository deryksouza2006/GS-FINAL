CREATE TABLE agronomic_analyses (
    id BIGSERIAL PRIMARY KEY,
    reading_id BIGINT NOT NULL UNIQUE REFERENCES sensor_readings(id) ON DELETE RESTRICT,
    crop_area_id BIGINT NOT NULL REFERENCES crop_areas(id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','PROCESSING','COMPLETED','FAILED')),
    risk_level VARCHAR(20) CHECK (risk_level IN ('LOW','MODERATE','HIGH','CRITICAL')),
    title VARCHAR(160), summary VARCHAR(2000), recommendation VARCHAR(4200),
    recommended_actions JSONB, observed_conditions JSONB, warnings JSONB,
    data_quality VARCHAR(20) CHECK (data_quality IN ('SUFFICIENT','PARTIAL','INSUFFICIENT')),
    retrieved_sources JSONB, model_name VARCHAR(100), prompt_version VARCHAR(30),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    error_message VARCHAR(200), started_at TIMESTAMPTZ, completed_at TIMESTAMPTZ,
    processing_token UUID, manual_retry_count INTEGER NOT NULL DEFAULT 0 CHECK (manual_retry_count >= 0),
    last_retry_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_analyses_crop_created ON agronomic_analyses(crop_area_id, created_at DESC, id DESC);
CREATE INDEX idx_analyses_status_started ON agronomic_analyses(status, started_at);

-- Table owned by this RAG corpus; do not reuse a previously auto-generated vector_store of another dimension.
CREATE TABLE agronomy_vector_store (
    id UUID PRIMARY KEY,
    content TEXT,
    metadata JSON,
    embedding vector(768)
);
CREATE INDEX idx_agronomy_vector_cosine ON agronomy_vector_store USING hnsw (embedding vector_cosine_ops);
CREATE TABLE rag_ingestion (
    corpus_key VARCHAR(180) PRIMARY KEY,
    checksum VARCHAR(64) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
