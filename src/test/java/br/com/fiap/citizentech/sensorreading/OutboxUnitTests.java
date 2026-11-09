package br.com.fiap.citizentech.sensorreading;

import br.com.fiap.citizentech.shared.outbox.*;
import br.com.fiap.citizentech.shared.messaging.*;
import br.com.fiap.citizentech.sensorreading.service.ReadingAnalysisService;
import br.com.fiap.citizentech.sensorreading.event.SensorReadingCreated;
import br.com.fiap.citizentech.sensorreading.repository.SensorReadingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OutboxUnitTests {
    private final OutboxProperties properties = new OutboxProperties(false, 10, 5000, 30000, 2, 100);

    @Test
    void publisherRecordsConfirmationAndKeepsFailureHistoryWithBoundedRetries() {
        var repo = mock(OutboxRepository.class);
        var sender = mock(OutboxEventSender.class);
        var publisher = new OutboxPublisher(repo, sender, properties);
        var success = event();
        when(repo.lockPending(10)).thenReturn(List.of(success));
        assertThat(publisher.publishPending()).isEqualTo(1);
        assertThat(success.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(success.getPublishedAt()).isNotNull();

        var failure = event();
        when(repo.lockPending(10)).thenReturn(List.of(failure));
        doThrow(new IllegalStateException("driver details must not be persisted")).when(sender).send(failure);
        publisher.publishPending();
        assertThat(failure.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(failure.getAttempts()).isEqualTo(1);
        assertThat(failure.getNextAttemptAt()).isAfter(Instant.now().plusSeconds(20));
        publisher.publishPending();
        assertThat(failure.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(failure.getAttempts()).isEqualTo(2);
        assertThat(failure.getLastError()).contains("IllegalStateException").doesNotContain("driver details");
        assertThat(failure.getPublishedAt()).isNull();
        verify(repo, never()).delete(any());
    }

    @Test
    void rabbitSenderRequiresAckAndRejectsUnroutableMessages() {
        var rabbit = mock(RabbitTemplate.class);
        var sender = new RabbitOutboxEventSender(rabbit, properties);
        var event = event();
        doAnswer(inv -> {
            Message message = inv.getArgument(2);
            CorrelationData correlation = inv.getArgument(3);
            assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
            assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        assertThatCode(() -> sender.send(event)).doesNotThrowAnyException();
        doAnswer(inv -> {
            CorrelationData correlation = inv.getArgument(3);
            correlation.setReturned(new ReturnedMessage(inv.getArgument(2), 312, "NO_ROUTE", "exchange", "key"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        assertThatThrownBy(() -> sender.send(event)).isInstanceOf(IllegalStateException.class);
        doAnswer(inv -> {
            CorrelationData correlation = inv.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "nack"));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        assertThatThrownBy(() -> sender.send(event)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingReadingThrowsSoTheListenerCanRetryAndDeadLetter() {
        var repo = mock(SensorReadingRepository.class);
        when(repo.findForUpdate(999L)).thenReturn(Optional.empty());
        var event = new SensorReadingCreated(UUID.randomUUID(), 999L, 1L, Instant.now(), null, null, null, null);
        assertThatThrownBy(() -> new ReadingAnalysisService(repo).queue(event))
                .isInstanceOf(IllegalStateException.class).hasMessage("Leitura do evento não encontrada");
        verify(repo, never()).saveAndFlush(any());
    }

    private OutboxEvent event() {
        return new OutboxEvent(UUID.randomUUID(), "SensorReading", 1L, "SensorReadingCreated", "{\"readingId\":1}");
    }
}
