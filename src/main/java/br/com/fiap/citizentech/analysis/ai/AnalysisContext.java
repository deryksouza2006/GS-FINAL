package br.com.fiap.citizentech.analysis.ai;

import br.com.fiap.citizentech.sensor.domain.SensorType;
import br.com.fiap.citizentech.sensorreading.domain.SensorReading;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AnalysisContext(String cropType, BigDecimal areaSize, String areaUnit, String description,
        String city, String state, Instant assembledAt, Measurement receivedReading,
        List<Measurement> latestBySensorType, List<String> activeSensorTypes, List<String> limitations) {
    public record Measurement(SensorType sensorType, Instant measuredAt, BigDecimal temperatureCelsius,
            BigDecimal airHumidityPercent, BigDecimal soilMoisturePercent, BigDecimal batteryPercent, boolean stale) {
        public static Measurement from(SensorReading r, Instant cutoff) {
            return new Measurement(r.getSensor().getSensorType(),r.getMeasuredAt(),r.getTemperatureCelsius(),
                    r.getAirHumidityPercent(),r.getSoilMoisturePercent(),r.getBatteryPercent(),r.getMeasuredAt().isBefore(cutoff));
        }
    }
}
