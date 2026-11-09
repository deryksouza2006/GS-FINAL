package br.com.fiap.citizentech.analysis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
@ConditionalOnProperty(name="app.ai.enabled",havingValue="true")
public class AnalysisRecoveryScheduler {
    private final AnalysisTransactions transactions;
    @Scheduled(fixedDelayString="${app.ai.recovery-interval-ms:30000}",initialDelayString="${app.ai.recovery-interval-ms:30000}")
    public void recover() { transactions.recoverAbandoned(); }
}
