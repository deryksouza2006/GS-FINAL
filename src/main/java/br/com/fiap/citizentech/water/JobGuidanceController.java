package br.com.fiap.citizentech.water;
import br.com.fiap.citizentech.analysis.ai.*;
import br.com.fiap.citizentech.analysis.config.AiProperties;
import br.com.fiap.citizentech.analysis.rag.AgronomyKnowledge;
import br.com.fiap.citizentech.sensor.domain.SensorType;
import br.com.fiap.citizentech.shared.exception.BusinessException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import lombok.RequiredArgsConstructor;

@RestController @RequiredArgsConstructor
public class JobGuidanceController {
    private final RiskJobService jobs;
    private final ObjectProvider<AgronomicAiClient> client;
    private final ObjectProvider<AgronomyKnowledge> knowledge;
    private final AiProperties properties;
    private final Validator validator;
    private final tools.jackson.databind.ObjectMapper mapper;
    private final br.com.fiap.citizentech.activity.ActivityService activities;
    private final ExecutorService executor=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4),
            Thread.ofPlatform().daemon().name("job-guidance-",0).factory(),new ThreadPoolExecutor.AbortPolicy());

    @PostMapping("/api/risk-jobs/{id}/guidance")
    public RiskJob generate(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id) {
        var job=jobs.get(jwt.getSubject(),id);
        if(job.getResult()==null) throw new BusinessException(HttpStatus.CONFLICT,"A análise ainda não terminou");
        if(job.getGuidance()!=null) return job;
        if(!properties.enabled() || client.getIfAvailable()==null || knowledge.getIfAvailable()==null)
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"Orientação de IA indisponível; resultado determinístico preservado");
        var recentActivities = activities.list(jwt.getSubject(), job.getCropAreaId(), null,
                job.getCreatedAt().minus(java.time.Duration.ofDays(30)), job.getCreatedAt(), 0, 10)
                .map(a -> Map.of("type", a.type().name(), "performedAt", a.performedAt().toString())).getContent();
        Future<Map<String,Object>> pending=null;
        try {
            pending=executor.submit(()-> {
                var measurements=job.getMeasurements().stream().map(m->new AnalysisContext.Measurement(SensorType.WEATHER_STATION,
                        m.measuredAt(),number(m.airTemperature()),number(m.airHumidity()),number(m.soilMoisture()),null,
                        m.measuredAt().isBefore(job.getCreatedAt().minus(properties.staleAfter())))).toList();
                var context=new AnalysisContext(job.getCropType(),null,null,
                        mapper.writeValueAsString(Map.of("fixedResult", job.getResult(),
                                "recentActivities", recentActivities,
                                "profile", Map.of("id", Objects.toString(job.getProfileId(), ""),
                                        "fieldCapacity", Objects.toString(job.getFieldCapacity(), ""),
                                        "wiltingPoint", Objects.toString(job.getWiltingPoint(), "")))),
                        null,null,job.getCreatedAt(),measurements.isEmpty()?null:measurements.getFirst(),measurements,
                        List.of(),job.getResult().limitations());
                var rag=knowledge.getObject().retrieve(context);
                if(!rag.sufficient()) throw new IllegalStateException("Acervo insuficiente");
                var advice=client.getObject().analyze(context,rag);
                if(advice==null || !validator.validate(advice).isEmpty()) throw new IllegalStateException("Orientação inválida");
                // The model's riskLevel is intentionally excluded: only the deterministic result is authoritative.
                return Map.<String,Object>of("summary",advice.summary(),"recommendation",advice.recommendation(),
                        "recommendedActions",advice.recommendedActions(),"warnings",advice.warnings(),"sources",rag.sources(),
                        "model",properties.model(),"promptVersion","agronomic-v1","generatedAt",java.time.Instant.now().toString());
            });
            var guidance=pending.get(properties.callTimeout().toMillis(),TimeUnit.MILLISECONDS);
            return jobs.saveGuidance(jwt.getSubject(),id,guidance);
        } catch(Exception failure) {
            if(pending!=null) pending.cancel(true);
            if(failure instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"Orientação indisponível; resultado determinístico preservado");
        }
    }
    private BigDecimal number(Double n) { return n==null ? null : BigDecimal.valueOf(n); }
    @PreDestroy void close() { executor.shutdownNow(); }
}
