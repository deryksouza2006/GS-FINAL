package br.com.fiap.citizentech.analysis.config;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;

@Validated
@ConfigurationProperties("app.ai")
public record AiProperties(boolean enabled, @NotBlank String model, @NotBlank String embeddingModel,
        @Min(3) @Max(5) int topK, @DecimalMin("0") @DecimalMax("1") double similarityThreshold,
        @NotNull Duration staleAfter, @NotNull Duration processingTimeout, @NotNull Duration callTimeout,
        @Min(1) @Max(10) int maxAttempts, @NotNull Duration retryCooldown,
        @Min(1) @Max(10) int maxManualRetries, @Min(2000) @Max(30000) int maxContextChars) {
    @AssertTrue(message="Os períodos de IA devem ser positivos e processingTimeout deve superar callTimeout")
    public boolean isTimingValid() {
        return staleAfter != null && !staleAfter.isNegative() && !staleAfter.isZero()
                && callTimeout != null && !callTimeout.isNegative() && !callTimeout.isZero()
                && processingTimeout != null && processingTimeout.compareTo(callTimeout) > 0
                && retryCooldown != null && !retryCooldown.isNegative() && !retryCooldown.isZero();
    }
}
