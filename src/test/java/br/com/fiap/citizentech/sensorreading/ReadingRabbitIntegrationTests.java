package br.com.fiap.citizentech.sensorreading;

import br.com.fiap.citizentech.PostgresTestConfiguration;
import br.com.fiap.citizentech.shared.messaging.ReadingMessagingConfig;
import br.com.fiap.citizentech.shared.outbox.*;
import br.com.fiap.citizentech.sensorreading.event.SensorReadingCreated;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {"app.readings.messaging-enabled=true", "app.outbox.enabled=false",
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms", "spring.rabbitmq.listener.simple.retry.max-interval=200ms"})
@AutoConfigureMockMvc
@Import({PostgresTestConfiguration.class, RabbitTestConfiguration.class})
class ReadingRabbitIntegrationTests extends ReadingTestSupport {
    @Autowired OutboxPublisher publisher;
    @Autowired OutboxRepository outbox;
    @Autowired RabbitTemplate rabbit;

    @Test
    void realPublicationIsConfirmedAndConsumerQueuesTheReading() throws Exception {
        var f = fixture("rabbit-" + UUID.randomUUID() + "@teste.com", "TEMPERATURE");
        try {
            long id = create(f, body(f)).get("id").asLong();
            assertThat(publisher.publishPending()).isEqualTo(1);
            var event = outbox.findAll().getFirst();
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
            assertThat(event.getPublishedAt()).isNotNull();
            await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).untilAsserted(() ->
                    assertThat(jdbc.queryForObject("SELECT analysis_status FROM sensor_readings WHERE id = ?", String.class, id))
                            .isEqualTo("QUEUED_FOR_ANALYSIS"));
        } finally { cleanup(f); }
    }

    @Test
    void nonexistentReadingIsRetriedAndDeliveredToTheRealDlq() throws Exception {
        var event = new SensorReadingCreated(UUID.randomUUID(), Long.MAX_VALUE, 1L, Instant.now(), null, null, null, null);
        rabbit.convertAndSend(ReadingMessagingConfig.EXCHANGE, ReadingMessagingConfig.ROUTING_KEY, event);
        var dead = new AtomicReference<Message>();
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
            if (dead.get() == null) dead.set(rabbit.receive(ReadingMessagingConfig.DLQ));
            assertThat(dead.get()).isNotNull();
        });
        assertThat(dead.get().getMessageProperties().getHeaders()).containsKey("x-death");
        assertThat(mapper.readTree(dead.get().getBody()).get("readingId").asLong()).isEqualTo(Long.MAX_VALUE);
    }
}
