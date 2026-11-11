package br.com.fiap.citizentech.shared.messaging;

import br.com.fiap.citizentech.shared.outbox.OutboxProperties;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OutboxProperties.class)
public class ReadingMessagingConfig {
    public static final String EXCHANGE = "citizentech.readings";
    public static final String ROUTING_KEY = "sensor.reading.created";
    public static final String QUEUE = "citizentech.reading-analysis";
    public static final String DLX = "citizentech.readings.dlx";
    public static final String DLQ = "citizentech.reading-analysis.dlq";
    public static final String DEAD_KEY = "sensor.reading.failed";

    @Bean
    public JacksonJsonMessageConverter readingMessageConverter(JsonMapper mapper) {
        return new JacksonJsonMessageConverter(mapper, "br.com.fiap.citizentech.sensorreading.event");
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.readings", name = "messaging-enabled", havingValue = "true", matchIfMissing = true)
    public Declarables readingTopology() {
        DirectExchange exchange = new DirectExchange(EXCHANGE, true, false);
        DirectExchange deadExchange = new DirectExchange(DLX, true, false);
        Queue queue = QueueBuilder.durable(QUEUE).deadLetterExchange(DLX).deadLetterRoutingKey(DEAD_KEY).build();
        Queue deadQueue = QueueBuilder.durable(DLQ).build();
        Queue riskQueue = QueueBuilder.durable("agroconecta.risk-jobs").deadLetterExchange(DLX).deadLetterRoutingKey("risk.failed").build();
        Queue riskDead = QueueBuilder.durable("agroconecta.risk-jobs.dlq").build();
        return new Declarables(exchange, deadExchange, queue, deadQueue, riskQueue, riskDead,
                BindingBuilder.bind(riskQueue).to(exchange).with("risk.analysis.requested"),
                BindingBuilder.bind(riskDead).to(deadExchange).with("risk.failed"),
                BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY),
                BindingBuilder.bind(deadQueue).to(deadExchange).with(DEAD_KEY));
    }
}
