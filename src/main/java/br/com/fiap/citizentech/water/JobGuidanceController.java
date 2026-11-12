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
        if(job.getGuidance()!=null && !Boolean.TRUE.equals(job.getGuidance().get("fallback"))) return job;
        if(job.getResult().riskLevel()==WaterRiskCalculator.Level.INSUFFICIENT_DATA)
            return fallback(job,"INSUFFICIENT_CONTEXT");
        if(!properties.enabled() || client.getIfAvailable()==null || knowledge.getIfAvailable()==null)
            return fallback(job,"PROVIDER_UNAVAILABLE");
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
                                "simulatedData", job.getMeasurements().stream().anyMatch(WaterRiskCalculator.Measurement::simulated),
                                "recentActivities", recentActivities,
                                "profile", Map.of("id", Objects.toString(job.getProfileId(), ""),
                                        "fieldCapacity", Objects.toString(job.getFieldCapacity(), ""),
                                        "wiltingPoint", Objects.toString(job.getWiltingPoint(), "")))),
                        null,null,job.getCreatedAt(),measurements.isEmpty()?null:measurements.getFirst(),measurements,
                        List.of(),job.getResult().limitations());
                var rag=knowledge.getObject().retrieve(context);
                if(!rag.sufficient()) throw new MissingSources();
                var advice=client.getObject().analyze(context,rag);
                if(advice==null || !validator.validate(advice).isEmpty()) throw new IllegalStateException("Orientação inválida");
                // The model's riskLevel is intentionally excluded: only the deterministic result is authoritative.
                var output=new LinkedHashMap<String,Object>();
                output.put("summary",advice.summary()); output.put("recommendation",advice.recommendation());
                output.put("recommendedActions",advice.recommendedActions()); output.put("signals",advice.observedConditions());
                var warnings=new ArrayList<>(advice.warnings()); warnings.addAll(limitations(job));
                output.put("warnings",warnings); output.put("sources",rag.sources());
                output.put("model",properties.model()); output.put("promptVersion","agronomic-v1");
                output.put("generatedAt",java.time.Instant.now().toString()); output.put("fallback",false);
                output.put("riskLevel",job.getResult().riskLevel().name());
                return output;
            });
            var guidance=pending.get(properties.callTimeout().toMillis(),TimeUnit.MILLISECONDS);
            return jobs.saveGuidance(jwt.getSubject(),id,guidance);
        } catch(Exception failure) {
            if(pending!=null) pending.cancel(true);
            if(failure instanceof InterruptedException) Thread.currentThread().interrupt();
            if(failure instanceof BusinessException business) throw business;
            return fallback(job,failure instanceof ExecutionException && failure.getCause() instanceof MissingSources
                    ? "NO_RELEVANT_SOURCES" : "PROVIDER_UNAVAILABLE");
        }
    }
    private static class MissingSources extends RuntimeException { }
    private List<String> limitations(RiskJob job) {
        var result=new ArrayList<>(job.getResult().limitations());
        if(job.getMeasurements().stream().anyMatch(WaterRiskCalculator.Measurement::simulated))
            result.add("As leituras utilizadas são simuladas");
        result.add("A orientação não substitui avaliação agronômica");
        return result;
    }
    private RiskJob fallback(RiskJob job,String reason) {
        // Not cached: a later request can retry generation after provider/corpus recovery.
        job.fallbackGuidance(Map.of("summary","Orientação automática indisponível; consulte o resultado determinístico.",
                "recommendation","Verifique os dados e o sensor com assistência técnica antes de decidir o manejo.",
                "recommendedActions",List.of("Conferir as medições e o perfil agronômico"),
                "signals",job.getResult().rulesTriggered(),"warnings",limitations(job),"sources",List.of(),
                "fallback",true,"reason",reason,"riskLevel",job.getResult().riskLevel().name(),
                "generatedAt",java.time.Instant.now().toString()));
        return job;
    }
    private BigDecimal number(Double n) { return n==null ? null : BigDecimal.valueOf(n); }
    @PreDestroy void close() { executor.shutdownNow(); }
}
