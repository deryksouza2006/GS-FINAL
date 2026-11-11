package br.com.fiap.citizentech.shared.messaging;

import br.com.fiap.citizentech.shared.outbox.*;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RabbitOutboxEventSender implements OutboxEventSender {
    private final RabbitTemplate rabbit;
    private final OutboxProperties properties;

    @Override
    public void send(OutboxEvent event) {
        boolean riskJob = "RiskAnalysisRequested".equals(event.getEventType());
        if (!riskJob && !"SensorReadingCreated".equals(event.getEventType())) {
            throw new IllegalArgumentException("Tipo de evento não suportado");
        }
        var correlation = new CorrelationData(event.getEventId() + "-" + event.getAttempts());
        var message = MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json").setContentEncoding("UTF-8")
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT).setMessageId(event.getEventId().toString())
                .setType(event.getEventType()).build();
        rabbit.send(ReadingMessagingConfig.EXCHANGE, riskJob ? "risk.analysis.requested" : ReadingMessagingConfig.ROUTING_KEY, message, correlation);
        try {
            var confirm = correlation.getFuture().get(properties.confirmTimeoutMs(), TimeUnit.MILLISECONDS);
            if (!confirm.ack() || correlation.getReturned() != null) {
                throw new IllegalStateException("Publicação recusada ou sem rota no broker");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Publicação interrompida", ex);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException ex) {
            throw new IllegalStateException("Confirmação do broker indisponível", ex);
        }
    }
}
