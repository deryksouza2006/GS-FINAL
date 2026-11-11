ALTER TABLE sensor_readings DROP CONSTRAINT ck_readings_source;
ALTER TABLE sensor_readings ADD CONSTRAINT ck_readings_source CHECK (source IN ('MANUAL','DEVICE','SIMULATED'));
ALTER TABLE sensor_readings DROP CONSTRAINT ck_readings_analysis_status;
ALTER TABLE sensor_readings ADD CONSTRAINT ck_readings_analysis_status CHECK (analysis_status IN ('RECORDED','PENDING_PUBLICATION','QUEUED_FOR_ANALYSIS','ANALYZED','FAILED'));
