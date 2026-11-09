package br.com.fiap.citizentech.analysis.dto;

import br.com.fiap.citizentech.analysis.domain.*;
import jakarta.validation.constraints.*;
import java.util.List;

public record AgronomicAdvice(
        @NotNull RiskLevel riskLevel,
        @NotBlank @Size(max=160) String title,
        @NotBlank @Size(max=2000) String summary,
        @NotBlank @Size(max=4000) String recommendation,
        @NotNull @Size(max=10) List<@NotBlank @Size(max=500) String> recommendedActions,
        @NotNull @Size(max=10) List<@NotBlank @Size(max=500) String> observedConditions,
        @NotNull @Size(max=10) List<@NotBlank @Size(max=500) String> warnings,
        @NotNull DataQuality dataQuality) { }
