package br.com.fiap.citizentech.water;

import br.com.fiap.citizentech.PostgresTestConfiguration;
import br.com.fiap.citizentech.analysis.ai.*;
import br.com.fiap.citizentech.analysis.domain.*;
import br.com.fiap.citizentech.analysis.dto.*;
import br.com.fiap.citizentech.analysis.rag.AgronomyKnowledge;
import br.com.fiap.citizentech.sensorreading.ReadingTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={"app.ai.enabled=true","app.ai.provider=fake","app.ai.call-timeout=2s"})
@AutoConfigureMockMvc
@Import({PostgresTestConfiguration.class,RiskJobGuidanceIntegrationTests.Config.class})
@WithMockUser(roles="PRODUCER")
class RiskJobGuidanceIntegrationTests extends ReadingTestSupport {
    @Autowired RiskJobService jobs;
    @Autowired RiskJobRepository repository;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ControlledClient client;
    @Autowired br.com.fiap.citizentech.sensorreading.repository.SensorReadingRepository readings;
    Fixture owner;
    final List<Fixture> fixtures=new ArrayList<>();
    String email;
    long jobId;

    @TestConfiguration(proxyBeanMethods=false)
    static class Config {
        @Bean ControlledClient controlledClient() { return new ControlledClient(); }
        @Bean AgronomyKnowledge controlledKnowledge() {
            return context -> new RagContext(List.of(new RagContext.Passage("Fonte institucional","Conferir o sensor.")),
                    List.of(new RetrievedSource("source-test","Manejo","Embrapa","https://www.embrapa.br","1")),true);
        }
    }
    static class ControlledClient implements AgronomicAiClient {
        final AtomicInteger calls=new AtomicInteger();
        volatile boolean unavailable;
        volatile AnalysisContext captured;
        public AgronomicAdvice analyze(AnalysisContext context,RagContext rag) {
            calls.incrementAndGet(); captured=context;
            if(unavailable) throw new IllegalStateException("private provider diagnostics");
            return new AgronomicAdvice(RiskLevel.LOW,"Observação","Conferir dados de campo.","Conferir o sensor.",
                    List.of("Inspecionar a instalação"),List.of("Solo seco"),List.of(),DataQuality.PARTIAL);
        }
    }
    Fixture own(String mail) throws Exception {
        var f=fixture(mail,"WEATHER_STATION");fixtures.add(f);return f;
    }
    @BeforeEach void setup() throws Exception {
        client.calls.set(0);client.unavailable=false;client.captured=null;
        email="guidance-"+UUID.randomUUID()+"@example.test";
        owner=own(email);
        create(owner,body(owner));
        jobId=json(send("POST","/api/crop-areas/"+owner.cropId()+"/risk-jobs",owner.token(),null)
                .andExpect(status().isAccepted())).get("id").asLong();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var job=repository.findById(jobId).orElseThrow();
            job.complete(new WaterRiskCalculator.Result(WaterRiskCalculator.Level.CRITICAL,15.0,15.0,1.3,-0.1,
                    List.of("LOW_WATER_AVAILABILITY"),List.of(),"demo-v1",true));
        });
        authorizeServiceCalls();
    }
    @AfterEach void cleanupAll() {
        for(var f:fixtures) {
            jdbc.update("DELETE FROM outbox_events WHERE aggregate_type='RiskAnalysisJob' AND aggregate_id IN (SELECT id FROM risk_jobs WHERE crop_area_id=?)",f.cropId());
            jdbc.update("DELETE FROM risk_jobs WHERE crop_area_id=?",f.cropId());
            jdbc.update("DELETE FROM field_activities WHERE crop_area_id=?",f.cropId());
            cleanup(f);
        }
        fixtures.clear();
    }
    String snapshot() {
        authorizeServiceCalls();
        var j=jobs.get(email,jobId);
        return mapper.writeValueAsString(List.of(j.getResult(),j.getMeasurements(),j.getThresholds(),j.getRainfall(),j.getAlertStatus()));
    }
    void authorizeServiceCalls() {
        var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                email,"unused",List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PRODUCER"))));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
    }
    Map<String,Object> fallback() {
        return Map.of("fallback",true,"reason","PROVIDER_UNAVAILABLE","summary","Indisponível",
                "recommendation","Conferir dados","recommendedActions",List.of("Conferir sensor"),"signals",List.of(),
                "warnings",List.of(),"sources",List.of(),"riskLevel","NORMAL","generatedAt",Instant.now().toString());
    }
    @Test void postFallbackIsCommittedAndGetReturnsExactlyThePersistedJson() throws Exception {
        String before=snapshot();client.unavailable=true;
        var posted=json(send("POST","/api/risk-jobs/"+jobId+"/guidance",owner.token(),null).andExpect(status().isOk()));
        var got=json(send("GET","/api/risk-jobs/"+jobId,owner.token(),null).andExpect(status().isOk()));
        assertThat(got.get("guidance")).isEqualTo(posted.get("guidance"));
        assertThat(got.get("guidance").get("fallback").asBoolean()).isTrue();
        assertThat(got.get("guidance").get("reason").asString()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(got.get("guidance").get("riskLevel").asString()).isEqualTo("CRITICAL");
        assertThat(got.get("status").asString()).isEqualTo("COMPLETED_WITHOUT_AI");
        assertThat(got.get("guidance").toString()).doesNotContain("private provider diagnostics");
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void explicitRetryReplacesFallbackAndSuccessIsPersistedAndReused() throws Exception {
        client.unavailable=true;
        send("POST","/api/risk-jobs/"+jobId+"/guidance",owner.token(),null).andExpect(status().isOk());
        client.unavailable=false;String before=snapshot();
        var posted=json(send("POST","/api/risk-jobs/"+jobId+"/guidance",owner.token(),null).andExpect(status().isOk()));
        var got=json(send("GET","/api/risk-jobs/"+jobId,owner.token(),null).andExpect(status().isOk()));
        assertThat(got.get("guidance")).isEqualTo(posted.get("guidance"));
        assertThat(got.get("guidance").get("fallback").asBoolean()).isFalse();
        assertThat(got.get("guidance").get("riskLevel").asString()).isEqualTo("CRITICAL");
        assertThat(got.get("guidance").get("sources").size()).isEqualTo(1);
        assertThat(got.get("status").asString()).isEqualTo("COMPLETED");
        var reused=json(send("POST","/api/risk-jobs/"+jobId+"/guidance",owner.token(),null).andExpect(status().isOk()));
        assertThat(reused.get("guidance")).isEqualTo(posted.get("guidance"));
        assertThat(client.calls).hasValue(2);
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void serviceAlwaysCopiesRiskFromLockedDeterministicResult() {
        String before=snapshot();
        jobs.saveFallbackGuidance(email,jobId,fallback());
        assertThat(jobs.get(email,jobId).getGuidance().get("riskLevel")).isEqualTo("CRITICAL");
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void manualProvenanceRequiresAllSnapshotReadingsToExistAndBelongToOwner() throws Exception {
        assertThat(jobs.hasOnlyManualReadings(email,jobId)).isTrue();
        Long readingId=jobs.get(email,jobId).getMeasurements().getFirst().id();
        jdbc.update("UPDATE sensor_readings SET source='DEVICE' WHERE id=?",readingId);
        assertThat(jobs.hasOnlyManualReadings(email,jobId)).isFalse();
        jdbc.update("UPDATE sensor_readings SET source='SIMULATED' WHERE id=?",readingId);
        assertThat(jobs.hasOnlyManualReadings(email,jobId)).isFalse();
        jdbc.update("UPDATE sensor_readings SET source='MANUAL' WHERE id=?",readingId);
        var other=own("origin-"+UUID.randomUUID()+"@example.test");
        authorizeServiceCalls();
        assertThat(readings.countByIdInAndSensorCropAreaFarmOwnerIdAndSource(List.of(readingId),other.userId(),
                br.com.fiap.citizentech.sensorreading.domain.ReadingSource.MANUAL)).isZero();
        assertThatThrownBy(()->jobs.hasOnlyManualReadings("unknown@example.test",jobId))
                .isInstanceOf(br.com.fiap.citizentech.shared.exception.BusinessException.class);
    }
    @Test void otherOwnerCannotReadGenerateOrSaveGuidance() throws Exception {
        String before=snapshot();var other=own("other-"+UUID.randomUUID()+"@example.test");
        send("GET","/api/risk-jobs/"+jobId,other.token(),null).andExpect(status().isNotFound());
        send("POST","/api/risk-jobs/"+jobId+"/guidance",other.token(),null).andExpect(status().isNotFound());
        String otherEmail=jdbc.queryForObject("SELECT email FROM users WHERE id=?",String.class,other.userId());
        authorizeServiceCalls();
        assertThatThrownBy(()->jobs.saveFallbackGuidance(otherEmail,jobId,fallback())).isInstanceOf(br.com.fiap.citizentech.shared.exception.BusinessException.class);
        assertThatThrownBy(()->jobs.saveGuidance(otherEmail,jobId,Map.of("fallback",false))).isInstanceOf(br.com.fiap.citizentech.shared.exception.BusinessException.class);
        assertThat(jobs.get(email,jobId).getGuidance()).isNull();
        assertThat(client.calls).hasValue(0);assertThat(snapshot()).isEqualTo(before);
    }
    @Test void concurrentFallbackAndSuccessCannotDowngradeValidGuidanceOrSnapshots() throws Exception {
        String before=snapshot();var start=new CountDownLatch(1);
        var pool=new DelegatingSecurityContextExecutorService(Executors.newFixedThreadPool(2));
        try {
            var a=pool.submit(()->{ start.await();return jobs.saveGuidance(email,jobId,Map.of("fallback",false,"summary","Explicação")); });
            var b=pool.submit(()->{ start.await();return jobs.saveFallbackGuidance(email,jobId,fallback()); });
            start.countDown();a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
        } finally {pool.shutdownNow();}
        var saved=jobs.get(email,jobId);
        assertThat(saved.getGuidance().get("fallback")).isEqualTo(false);
        assertThat(saved.getStatus()).isEqualTo("COMPLETED");
        assertThat(snapshot()).isEqualTo(before);
        jobs.saveFallbackGuidance(email,jobId,fallback());
        assertThat(jobs.get(email,jobId).getGuidance()).isEqualTo(saved.getGuidance());
    }
    @Test void promptContextUsesOnlyOwnedSnapshotAndActivityTypesNotOtherProducers() throws Exception {
        var other=own("private-"+UUID.randomUUID()+"@example.test");
        jdbc.update("UPDATE crop_areas SET crop_type='OTHER_PRODUCER_PRIVATE' WHERE id=?",other.cropId());
        jdbc.update("INSERT INTO field_activities(crop_area_id,author_id,type,performed_at,description,created_at,updated_at) VALUES (?,?, 'PLANTING',CURRENT_TIMESTAMP-INTERVAL '1 hour','OTHER_PRODUCER_PRIVATE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",other.cropId(),other.userId());
        send("POST","/api/risk-jobs/"+jobId+"/guidance",owner.token(),null).andExpect(status().isOk());
        String context=mapper.writeValueAsString(client.captured);
        assertThat(context).contains("fixedResult","CRITICAL","Milho").doesNotContain("OTHER_PRODUCER_PRIVATE",email,"private-","deviceCode","Bearer");
    }
}
