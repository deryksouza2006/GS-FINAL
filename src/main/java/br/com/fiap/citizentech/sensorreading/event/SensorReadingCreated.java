package br.com.fiap.citizentech.sensorreading.event;

import br.com.fiap.citizentech.sensorreading.domain.SensorReading;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SensorReadingCreated(UUID eventId, Long readingId, Long sensorId, Instant measuredAt,
        BigDecimal temperatureCelsius, BigDecimal airHumidityPercent, BigDecimal soilMoisturePercent, BigDecimal batteryPercent) {
    public static SensorReadingCreated from(UUID eventId, SensorReading reading) {
        return new SensorReadingCreated(eventId, reading.getId(), reading.getSensor().getId(), reading.getMeasuredAt(),
                reading.getTemperatureCelsius(), reading.getAirHumidityPercent(), reading.getSoilMoisturePercent(), reading.getBatteryPercent());
    }
}
