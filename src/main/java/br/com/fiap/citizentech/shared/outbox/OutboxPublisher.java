package br.com.fiap.citizentech.shared.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisher {
    private final OutboxRepository events;
    private final OutboxEventSender sender;
    private final OutboxProperties properties;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    // Uma transação própria nunca enxerga eventos ainda não confirmados pela transação da API.
    // Os bloqueios são mantidos até gravar o resultado da publicação; outra instância usa SKIP LOCKED.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int publishPending() {
        var batch = events.lockPending(properties.batchSize());
        for (OutboxEvent event : batch) {
            try {
                sender.send(event);
                event.published();
            } catch (RuntimeException ex) {
                // Não persistir mensagens arbitrárias de drivers, URLs ou credenciais.
                event.publicationFailed("Falha na publicação: " + ex.getClass().getSimpleName(),
                        properties.maxAttempts(), properties.retryDelayMs());
                log.warn("Falha ao publicar evento {}; tentativa {}", event.getEventId(), event.getAttempts());
            }
            if ("RiskAnalysisRequested".equals(event.getEventType())) {
                if (event.getStatus() == OutboxStatus.PUBLISHED) {
                    jdbc.update("UPDATE risk_jobs SET status='QUEUED' WHERE id=? AND status='PENDING'",
                            event.getAggregateId());
                } else if (event.getStatus() == OutboxStatus.FAILED) {
                    jdbc.update("""
                            UPDATE risk_jobs SET status='FAILED', completed_at=CURRENT_TIMESTAMP,
                            error_message='Publicação no broker esgotou as tentativas'
                            WHERE id=? AND status IN ('PENDING','QUEUED')
                            """, event.getAggregateId());
                }
            }
        }
        events.flush();
        return batch.size();
    }
}
