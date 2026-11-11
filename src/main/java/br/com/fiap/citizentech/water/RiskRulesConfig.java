package br.com.fiap.citizentech.water;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RiskRulesConfig {
    @Bean
    WaterRiskCalculator.Thresholds riskThresholds(
            @Value("${app.risk.critical-availability:20}") double critical,
            @Value("${app.risk.attention-availability:40}") double attention,
            @Value("${app.risk.high-vpd:2}") double vpd,
            @Value("${app.risk.rapid-drying:-3}") double drying,
            @Value("${app.risk.stale-after:24h}") Duration stale,
            @Value("${app.risk.rules-version:demo-v1}") String version) {
        return new WaterRiskCalculator.Thresholds(critical, attention, vpd, drying, stale, version);
    }
}
