package br.com.fiap.citizentech.water;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.amqp.core.Message;
import tools.jackson.databind.ObjectMapper;
import java.time.Duration;
@Service @RequiredArgsConstructor
@ConditionalOnProperty(prefix="app.readings",name="messaging-enabled",havingValue="true",matchIfMissing=true)
public class RiskJobConsumer {
    private final RiskJobRepository jobs;
    private final ObjectMapper mapper;
    @RabbitListener(queues="agroconecta.risk-jobs") @Transactional
    public void consume(Message message) {
        long id=mapper.readTree(message.getBody()).required("jobId").asLong();
        var job=jobs.lock(id).orElseThrow(()->new org.springframework.amqp.AmqpRejectAndDontRequeueException("Job ausente"));
        if (job.terminal()) return;
        job.start();
        var rules = job.getThresholds() == null
                ? new WaterRiskCalculator.Thresholds(20,40,2,-3,Duration.ofHours(24),job.getRulesVersion())
                : job.getThresholds();
        job.complete(new WaterRiskCalculator().calculate(job.getMeasurements(),job.getFieldCapacity(),job.getWiltingPoint(),job.getCreatedAt(),rules,job.isDemo()));
    }
}
