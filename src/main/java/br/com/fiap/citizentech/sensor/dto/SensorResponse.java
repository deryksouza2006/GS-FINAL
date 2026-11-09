package br.com.fiap.citizentech.sensor.dto;

import br.com.fiap.citizentech.sensor.domain.Sensor;
import br.com.fiap.citizentech.sensor.domain.SensorStatus;
import br.com.fiap.citizentech.sensor.domain.SensorType;

import java.time.Instant;
import java.time.LocalDate;

public record SensorResponse(Long id, Long cropAreaId, String cropAreaName, Long farmId, String farmName,
                             String name, String deviceCode, SensorType sensorType, SensorStatus status,
                             LocalDate installationDate, String description, Instant createdAt, Instant updatedAt) {
    public static SensorResponse from(Sensor sensor) {
        var cropArea = sensor.getCropArea();
        var farm = cropArea.getFarm();
        return new SensorResponse(sensor.getId(), cropArea.getId(), cropArea.getName(), farm.getId(), farm.getName(),
                sensor.getName(), sensor.getDeviceCode(), sensor.getSensorType(), sensor.getStatus(),
                sensor.getInstallationDate(), sensor.getDescription(), sensor.getCreatedAt(), sensor.getUpdatedAt());
    }
}
