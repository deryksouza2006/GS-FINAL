ALTER TABLE crop_areas DROP CONSTRAINT ck_crop_areas_status;
ALTER TABLE crop_areas ADD CONSTRAINT ck_crop_areas_status CHECK (status IN ('NORMAL','ATTENTION','CRITICAL','ARCHIVED'));
