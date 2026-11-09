package br.com.fiap.citizentech.sensorreading.messaging;

import br.com.fiap.citizentech.sensorreading.event.SensorReadingCreated;
import br.com.fiap.citizentech.sensorreading.service.ReadingAnalysisService;
import br.com.fiap.citizentech.shared.messaging.ReadingMessagingConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.readings", name = "messaging-enabled", havingValue = "true", matchIfMissing = true)
public class ReadingCreatedListener {
    private final ReadingAnalysisService analysis;
    private final br.com.fiap.citizentech.analysis.service.AgronomicAnalysisService agronomicAnalysis;

    @RabbitListener(queues = ReadingMessagingConfig.QUEUE)
    public void onCreated(SensorReadingCreated event) {
        analysis.queue(event);
        agronomicAnalysis.process(event.readingId());
    }
}
