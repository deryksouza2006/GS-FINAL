ALTER TABLE risk_jobs ADD COLUMN recovery_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE risk_jobs ADD COLUMN acknowledged_by BIGINT REFERENCES users(id);
ALTER TABLE risk_jobs ADD COLUMN resolved_by BIGINT REFERENCES users(id);
CREATE INDEX idx_risk_jobs_recovery ON risk_jobs(id) WHERE status='QUEUED';
