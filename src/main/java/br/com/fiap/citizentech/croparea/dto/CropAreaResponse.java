package br.com.fiap.citizentech.croparea.dto;

import br.com.fiap.citizentech.croparea.domain.AreaUnit;
import br.com.fiap.citizentech.croparea.domain.CropArea;
import br.com.fiap.citizentech.croparea.domain.CropAreaStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record CropAreaResponse(Long id, Long farmId, String farmName, String name, String cropType,
                               BigDecimal areaSize, AreaUnit areaUnit, CropAreaStatus status,
                               BigDecimal latitude, BigDecimal longitude, String description,
                               Instant createdAt, Instant updatedAt) {
    public static CropAreaResponse from(CropArea area) {
        return new CropAreaResponse(area.getId(), area.getFarm().getId(), area.getFarm().getName(),
                area.getName(), area.getCropType(), area.getAreaSize(), area.getAreaUnit(), area.getStatus(),
                area.getLatitude(), area.getLongitude(), area.getDescription(), area.getCreatedAt(), area.getUpdatedAt());
    }
}
