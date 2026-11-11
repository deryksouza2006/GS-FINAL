CREATE TABLE farm_technicians (
 farm_id BIGINT NOT NULL REFERENCES farms(id),
 technician_id BIGINT NOT NULL REFERENCES users(id),
 PRIMARY KEY(farm_id,technician_id)
);
ALTER TABLE sensors ADD COLUMN calibrated_at DATE;
ALTER TABLE sensors ADD COLUMN maintenance_note VARCHAR(500);
ALTER TABLE sensors DROP CONSTRAINT ck_sensors_status;
ALTER TABLE sensors ADD CONSTRAINT ck_sensors_status CHECK(status IN ('ACTIVE','INACTIVE','FAULT','MAINTENANCE'));
