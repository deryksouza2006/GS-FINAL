package br.com.fiap.citizentech.sensor.dto;

import br.com.fiap.citizentech.sensor.domain.SensorType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Locale;

public record CreateSensorRequest(
        @NotNull(message = "Área de cultivo obrigatória")
        @Positive(message = "ID da área de cultivo deve ser positivo") Long cropAreaId,
        @NotBlank(message = "Nome obrigatório")
        @Size(min = 3, max = 100, message = "Nome deve ter entre 3 e 100 caracteres") String name,
        @NotBlank(message = "Código do dispositivo obrigatório")
        @Size(min = 3, max = 80, message = "Código do dispositivo deve ter entre 3 e 80 caracteres") String deviceCode,
        @NotNull(message = "Tipo de sensor obrigatório") SensorType sensorType,
        @PastOrPresent(message = "Data de instalação não pode estar no futuro") LocalDate installationDate,
        @Size(max = 500, message = "Descrição deve ter no máximo 500 caracteres") String description) {

    public CreateSensorRequest {
        name = name == null ? null : name.trim();
        deviceCode = deviceCode == null ? null : deviceCode.trim().toUpperCase(Locale.ROOT);
    }
}
