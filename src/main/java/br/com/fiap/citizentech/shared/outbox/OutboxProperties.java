package br.com.fiap.citizentech.shared.outbox;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(boolean enabled, @Min(1) @Max(100) int batchSize,
        @Min(100) long pollIntervalMs, @Min(100) long retryDelayMs,
        @Min(1) int maxAttempts, @Min(100) @Max(30000) long confirmTimeoutMs) {
}
