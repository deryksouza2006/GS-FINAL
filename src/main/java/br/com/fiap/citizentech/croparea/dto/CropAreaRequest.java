package br.com.fiap.citizentech.croparea.dto;

import br.com.fiap.citizentech.croparea.domain.AreaUnit;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CropAreaRequest(
        @NotNull(message = "Propriedade obrigatória") @Positive(message = "ID da propriedade deve ser positivo") Long farmId,
        @NotBlank(message = "Nome obrigatório")
        @Size(min = 3, max = 120, message = "Nome deve ter entre 3 e 120 caracteres") String name,
        @NotBlank(message = "Tipo de cultivo obrigatório")
        @Size(min = 2, max = 80, message = "Tipo de cultivo deve ter entre 2 e 80 caracteres") String cropType,
        @NotNull(message = "Área obrigatória") @Positive(message = "Área deve ser positiva")
        @Digits(integer = 10, fraction = 2, message = "Área deve ter até 10 dígitos inteiros e 2 decimais") BigDecimal areaSize,
        @NotNull(message = "Unidade de área obrigatória") AreaUnit areaUnit,
        @DecimalMin(value = "-90", message = "Latitude deve estar entre -90 e 90")
        @DecimalMax(value = "90", message = "Latitude deve estar entre -90 e 90")
        @Digits(integer = 3, fraction = 6, message = "Latitude deve ter até 6 casas decimais") BigDecimal latitude,
        @DecimalMin(value = "-180", message = "Longitude deve estar entre -180 e 180")
        @DecimalMax(value = "180", message = "Longitude deve estar entre -180 e 180")
        @Digits(integer = 3, fraction = 6, message = "Longitude deve ter até 6 casas decimais") BigDecimal longitude,
        @Size(max = 500, message = "Descrição deve ter no máximo 500 caracteres") String description) {

    public CropAreaRequest {
        name = name == null ? null : name.trim();
        cropType = cropType == null ? null : cropType.trim();
    }

    @AssertTrue(message = "Latitude e longitude devem ser informadas juntas")
    @JsonIgnore
    public boolean isCoordinatesComplete() {
        return (latitude == null) == (longitude == null);
    }
}
