package br.com.fiap.citizentech.water;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
public record ProfileRequest(@NotBlank @Size(max=100) String soilTexture,
        @NotNull @DecimalMin("0") @DecimalMax("100") Double fieldCapacityPercent,
        @NotNull @DecimalMin("0") @DecimalMax("100") Double wiltingPointPercent,
        @Positive Double rootDepthCentimeters,@Positive Double sensorDepthCentimeters,
        @NotBlank @Size(max=500) String source,boolean demo) {
    @AssertTrue(message="Capacidade de campo deve superar ponto de murcha") @JsonIgnore
    public boolean isOrdered() {
        return fieldCapacityPercent!=null && wiltingPointPercent!=null
                && Double.isFinite(fieldCapacityPercent) && Double.isFinite(wiltingPointPercent)
                && fieldCapacityPercent>wiltingPointPercent;
    }
}
