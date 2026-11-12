package br.com.fiap.citizentech.sensorreading.dto;

import br.com.fiap.citizentech.sensorreading.domain.ReadingSource;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateReadingRequest(
        @NotNull(message = "Sensor obrigatório") @Positive(message = "ID do sensor deve ser positivo") Long sensorId,
        @DecimalMin(value = "-50", message = "Temperatura deve estar entre -50 e 70")
        @DecimalMax(value = "70", message = "Temperatura deve estar entre -50 e 70")
        @Digits(integer = 3, fraction = 3, message = "Temperatura deve ter até 3 casas decimais") BigDecimal temperatureCelsius,
        @DecimalMin(value = "0", message = "Umidade do ar deve estar entre 0 e 100")
        @DecimalMax(value = "100", message = "Umidade do ar deve estar entre 0 e 100")
        @Digits(integer = 3, fraction = 3, message = "Umidade deve ter até 3 casas decimais") BigDecimal airHumidityPercent,
        @DecimalMin(value = "0", message = "Umidade do solo deve estar entre 0 e 100")
        @DecimalMax(value = "100", message = "Umidade do solo deve estar entre 0 e 100")
        @Digits(integer = 3, fraction = 3, message = "Umidade deve ter até 3 casas decimais") BigDecimal soilMoisturePercent,
        @DecimalMin(value = "0", message = "Bateria deve estar entre 0 e 100")
        @DecimalMax(value = "100", message = "Bateria deve estar entre 0 e 100")
        @Digits(integer = 3, fraction = 3, message = "Bateria deve ter até 3 casas decimais") BigDecimal batteryPercent,
        Instant measuredAt, ReadingSource source,
        @DecimalMin("0") @Digits(integer=6,fraction=3) BigDecimal rainfallMillimeters,
        Instant rainfallIntervalStart) {
    @AssertTrue(message="Chuva requer intervalo de medição válido") @JsonIgnore
    public boolean isRainfallIntervalValid() {
        return rainfallMillimeters==null ? rainfallIntervalStart==null
                : rainfallIntervalStart!=null && measuredAt!=null && rainfallIntervalStart.isBefore(measuredAt);
    }
    @AssertTrue(message = "Informe pelo menos uma medição") @JsonIgnore
    public boolean isMeasurementPresent() {
        return temperatureCelsius != null || airHumidityPercent != null || soilMoisturePercent != null || batteryPercent != null || rainfallMillimeters!=null;
    }
    @AssertTrue(message = "Origem inválida para recebimento externo") @JsonIgnore
    public boolean isManualSource() {
        return source == null || source == ReadingSource.MANUAL || source == ReadingSource.SIMULATED;
    }
}
