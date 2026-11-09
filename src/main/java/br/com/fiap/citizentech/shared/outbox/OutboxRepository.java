package br.com.fiap.citizentech.shared.outbox;

import org.springframework.data.jpa.repository.*;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP
            ORDER BY created_at, event_id LIMIT :batchSize FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockPending(int batchSize);

    @Modifying
    @Query("""
            update OutboxEvent e set e.status = br.com.fiap.citizentech.shared.outbox.OutboxStatus.FAILED,
                e.lastError = :reason
            where e.aggregateType = :aggregateType and e.aggregateId = :aggregateId
                and e.status = br.com.fiap.citizentech.shared.outbox.OutboxStatus.PENDING
            """)
    int cancelPending(String aggregateType, Long aggregateId, String reason);
}
