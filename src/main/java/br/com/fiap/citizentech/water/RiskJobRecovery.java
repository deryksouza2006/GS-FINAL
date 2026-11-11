package br.com.fiap.citizentech.water;

import br.com.fiap.citizentech.shared.outbox.OutboxEvent;
import br.com.fiap.citizentech.shared.outbox.OutboxRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name="app.outbox.enabled", havingValue="true", matchIfMissing=true)
class RiskJobRecovery {
    private final RiskJobRepository jobs;
    private final OutboxRepository outbox;
    private final ObjectMapper mapper;

    @Scheduled(fixedDelayString="${app.risk.recovery-delay-ms:60000}")
    @Transactional
    public void recover() {
        for (RiskJob job : jobs.lockAbandoned(Instant.now().minus(Duration.ofMinutes(15)))) {
            if (job.getRecoveryCount() >= 3) {
                job.fail();
            } else {
                job.requeue();
                outbox.save(new OutboxEvent(UUID.randomUUID(), "RiskAnalysisJob", job.getId(),
                        "RiskAnalysisRequested", mapper.writeValueAsString(Map.of("jobId", job.getId()))));
            }
        }
    }
}
