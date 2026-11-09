package br.com.fiap.citizentech.farm.dto;

import br.com.fiap.citizentech.croparea.domain.AreaUnit;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Locale;

public record FarmRequest(
        @NotBlank(message = "Nome obrigatório")
        @Size(min = 3, max = 100, message = "Nome deve ter entre 3 e 100 caracteres") String name,
        @NotBlank(message = "Cidade obrigatória")
        @Size(max = 100, message = "Cidade deve ter no máximo 100 caracteres") String city,
        @NotBlank(message = "Estado obrigatório")
        @Pattern(regexp = "AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO",
                message = "Estado deve ser uma sigla brasileira válida com 2 letras") String state,
        @NotNull(message = "Área total obrigatória")
        @Positive(message = "Área total deve ser positiva")
        @Digits(integer = 10, fraction = 2, message = "Área total deve ter até 10 dígitos inteiros e 2 decimais")
        BigDecimal totalArea,
        @NotNull(message = "Unidade de área obrigatória") AreaUnit areaUnit) {

    public FarmRequest {
        name = name == null ? null : name.trim();
        city = city == null ? null : city.trim();
        state = state == null ? null : state.trim().toUpperCase(Locale.ROOT);
    }
}
