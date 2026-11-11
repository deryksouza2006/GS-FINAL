package br.com.fiap.citizentech.sensorreading.dto;

import br.com.fiap.citizentech.sensor.domain.SensorType;
import br.com.fiap.citizentech.sensorreading.domain.*;
import java.math.BigDecimal;
import java.time.Instant;

public record ReadingResponse(Long id, Long sensorId, String sensorName, SensorType sensorType,
        Long cropAreaId, String cropAreaName, Long farmId, String farmName,
        BigDecimal temperatureCelsius, BigDecimal airHumidityPercent, BigDecimal soilMoisturePercent,
        BigDecimal batteryPercent, Instant measuredAt, ReadingSource source, ReadingAnalysisStatus analysisStatus,
        Instant createdAt, Instant updatedAt, BigDecimal rainfallMillimeters, Instant rainfallIntervalStart) {
    public static ReadingResponse from(SensorReading reading) {
        var sensor = reading.getSensor();
        var crop = sensor.getCropArea();
        var farm = crop.getFarm();
        return new ReadingResponse(reading.getId(), sensor.getId(), sensor.getName(), sensor.getSensorType(),
                crop.getId(), crop.getName(), farm.getId(), farm.getName(), reading.getTemperatureCelsius(),
                reading.getAirHumidityPercent(), reading.getSoilMoisturePercent(), reading.getBatteryPercent(),
                reading.getMeasuredAt(), reading.getSource(), reading.getAnalysisStatus(), reading.getCreatedAt(), reading.getUpdatedAt(),
                reading.getRainfallMillimeters(),reading.getRainfallIntervalStart());
    }
}
