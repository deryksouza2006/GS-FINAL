package br.com.fiap.citizentech.shared.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxSchedulingConfig {
    private final OutboxPublisher publisher;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms}", initialDelayString = "${app.outbox.poll-interval-ms}")
    public void publish() {
        publisher.publishPending();
    }
}
