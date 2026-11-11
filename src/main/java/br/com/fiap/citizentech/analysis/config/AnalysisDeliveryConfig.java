package br.com.fiap.citizentech.analysis.config;

import br.com.fiap.citizentech.analysis.service.AnalysisTransactions;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.context.annotation.*;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods=false)
public class AnalysisDeliveryConfig {
    @Bean
    MessageRecoverer analysisMessageRecoverer(AnalysisTransactions transactions,ObjectMapper mapper,AiProperties properties,
            br.com.fiap.citizentech.water.RiskJobFailureService riskFailures) {
        return (message,cause) -> {
            try {
                var payload=mapper.readTree(message.getBody());
                if(payload.has("jobId")) riskFailures.fail(payload.get("jobId").asLong());
            } catch(RuntimeException ignored) { }
            if(properties.enabled()) {
                try {
                    var node=mapper.readTree(message.getBody());
                    if(node.has("readingId")) transactions.exhausted(node.get("readingId").asLong());
                } catch(RuntimeException ignored) {
                    // Malformed/unavailable resources still follow the existing dead-letter path.
                }
            }
            new RejectAndDontRequeueRecoverer().recover(message,new IllegalStateException("Processamento da leitura esgotado"));
        };
    }
}
