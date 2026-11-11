package br.com.fiap.citizentech.activity;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.math.BigDecimal;
public record ActivityRequest(@NotNull FieldActivity.Type type, @NotNull @PastOrPresent Instant performedAt,
        @Size(max=2000) String description, @Positive @Digits(integer=11,fraction=3) BigDecimal quantity,
        @Size(max=30) String unit) {
    @AssertTrue(message="Quantidade e unidade devem ser informadas juntas") @JsonIgnore
    public boolean isQuantityConsistent() { return quantity==null ? unit==null : unit!=null && !unit.isBlank(); }
}
