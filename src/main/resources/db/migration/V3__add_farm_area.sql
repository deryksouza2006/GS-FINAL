-- Propriedades existentes permanecem sem área informada até a próxima atualização.
ALTER TABLE farms ADD COLUMN total_area NUMERIC(12, 2);
ALTER TABLE farms ADD COLUMN area_unit VARCHAR(20);

ALTER TABLE farms ADD CONSTRAINT ck_farms_area CHECK (
    (total_area IS NULL AND area_unit IS NULL)
    OR (
        total_area IS NOT NULL AND area_unit IS NOT NULL
        AND total_area > 0
        AND area_unit IN ('HECTARE', 'SQUARE_METER')
    )
);
