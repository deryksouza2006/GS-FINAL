package br.com.fiap.citizentech.sensor.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record SensorPageResponse(List<SensorResponse> content, int page, int size,
                                 long totalElements, int totalPages) {
    public static SensorPageResponse from(Page<SensorResponse> sensors) {
        return new SensorPageResponse(sensors.getContent(), sensors.getNumber(), sensors.getSize(),
                sensors.getTotalElements(), sensors.getTotalPages());
    }
}
