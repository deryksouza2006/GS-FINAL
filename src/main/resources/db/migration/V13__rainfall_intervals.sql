ALTER TABLE sensor_readings ADD COLUMN rainfall_millimeters NUMERIC(9,3);
ALTER TABLE sensor_readings ADD COLUMN rainfall_interval_start TIMESTAMPTZ;
ALTER TABLE sensor_readings ADD CONSTRAINT ck_rainfall_interval CHECK (
 (rainfall_millimeters IS NULL AND rainfall_interval_start IS NULL) OR
 (rainfall_millimeters >= 0 AND rainfall_interval_start IS NOT NULL AND rainfall_interval_start < measured_at));
ALTER TABLE sensor_readings DROP CONSTRAINT ck_readings_measurement;
ALTER TABLE sensor_readings ADD CONSTRAINT ck_readings_measurement CHECK
 (temperature_celsius IS NOT NULL OR air_humidity_percent IS NOT NULL OR soil_moisture_percent IS NOT NULL
 OR battery_percent IS NOT NULL OR rainfall_millimeters IS NOT NULL);
