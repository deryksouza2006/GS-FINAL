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
        }
        events.flush();
        return batch.size();
    }
}
