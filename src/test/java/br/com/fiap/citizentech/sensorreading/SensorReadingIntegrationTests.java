package br.com.fiap.citizentech.sensorreading;

import br.com.fiap.citizentech.PostgresTestConfiguration;
import br.com.fiap.citizentech.sensorreading.repository.SensorReadingRepository;
import br.com.fiap.citizentech.sensorreading.service.ReadingAnalysisService;
import br.com.fiap.citizentech.sensorreading.event.SensorReadingCreated;
import br.com.fiap.citizentech.shared.outbox.*;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.outbox.max-attempts=2")
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class SensorReadingIntegrationTests extends ReadingTestSupport {
    @Autowired SensorReadingRepository readings;
    @Autowired EntityManager em;
    @Autowired OutboxPublisher publisher;
    @Autowired ReadingAnalysisService analysis;
    @MockitoSpyBean OutboxRepository outbox;
    @MockitoBean OutboxEventSender sender;

    @ParameterizedTest
    @CsvSource({"SOIL_MOISTURE,soilMoisturePercent", "TEMPERATURE,temperatureCelsius",
            "HUMIDITY,airHumidityPercent", "WEATHER_STATION,batteryPercent"})
    void createsForEachTypeWithDefaultDateAndPrivateAtomicPayload(String type, String measurement) throws Exception {
        var f = fixture("produtor@teste.com", type);
        Instant before = Instant.now();
        var body = new HashMap<String, Object>();
        body.put("sensorId", f.sensorId());
        body.put(measurement, 30);
        var response = create(f, body);
        assertThat(response.size()).isEqualTo(17);
        assertThat(response.get("source").asString()).isEqualTo("MANUAL");
        assertThat(response.get("analysisStatus").asString()).isEqualTo("PENDING_PUBLICATION");
        assertThat(Instant.parse(response.get("measuredAt").asString())).isBetween(before, Instant.now());
        assertThat(response.get("farmId").asLong()).isEqualTo(f.farmId());
        assertThat(readings.count()).isEqualTo(1);
        assertThat(outbox.count()).isEqualTo(1);
        var event = outbox.findAll().getFirst();
        assertThat(event.getEventType()).isEqualTo("SensorReadingCreated");
        assertThat(event.getAggregateId()).isEqualTo(response.get("id").asLong());
        var payload = mapper.readTree(event.getPayload());
        assertThat(payload.size()).isEqualTo(8);
        assertThat(payload.has("email")).isFalse();
        assertThat(payload.has("name")).isFalse();
        assertThat(payload.toString()).doesNotContain("Produtor", "@teste.com", "Sensor Teste", "Fazenda");
        assertThat(jdbc.queryForObject("SELECT jsonb_typeof(payload) FROM outbox_events WHERE event_id = ?", String.class, event.getEventId()))
                .isEqualTo("object");
        // REQUIRES_NEW consulta outra transação: nenhum evento não commitado pode ser publicado.
        assertThat(publisher.publishPending()).isZero();
        verifyNoInteractions(sender);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SOIL_MOISTURE", "TEMPERATURE", "HUMIDITY"})
    void requiresTheMeasurementForTheSensorType(String type) throws Exception {
        var f = fixture("produtor@teste.com", type);
        send("POST", "/api/sensor-readings", f.token(), Map.of("sensorId", f.sensorId(), "batteryPercent", 50))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Informe a medição obrigatória para o tipo do sensor"));
        assertThat(readings.count()).isZero();
        assertThat(outbox.count()).isZero();
    }

    @Test
    void validatesMeasurementsLimitsTimeAndManualSource() throws Exception {
        var f = fixture("produtor@teste.com", "WEATHER_STATION");
        send("POST", "/api/sensor-readings", f.token(), Map.of("temperatureCelsius", 25))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.sensorId").exists());
        send("POST", "/api/sensor-readings", f.token(), Map.of("sensorId", f.sensorId()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.measurementPresent").exists());
        for (int side : List.of(-1, 1)) {
            var b = body(f);
            b.put("temperatureCelsius", side < 0 ? -51 : 71);
            b.put("airHumidityPercent", side < 0 ? -1 : 101);
            b.put("soilMoisturePercent", side < 0 ? -1 : 101);
            b.put("batteryPercent", side < 0 ? -1 : 101);
            send("POST", "/api/sensor-readings", f.token(), b).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.temperatureCelsius").exists()).andExpect(jsonPath("$.fields.airHumidityPercent").exists())
                    .andExpect(jsonPath("$.fields.soilMoisturePercent").exists()).andExpect(jsonPath("$.fields.batteryPercent").exists());
        }
        var b = body(f);
        b.put("measuredAt", Instant.now().plusSeconds(600).toString());
        send("POST", "/api/sensor-readings", f.token(), b).andExpect(status().isBadRequest());
        b.put("measuredAt", Instant.now().plusSeconds(240).toString());
        b.put("source", "DEVICE");
        send("POST", "/api/sensor-readings", f.token(), b).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.manualSource").exists());
        b.put("source", "MANUAL");
        b.put("temperatureCelsius", -50);
        b.put("airHumidityPercent", 100);
        b.put("soilMoisturePercent", 0);
        b.put("batteryPercent", 100);
        var reading = create(f, b);
        em.clear();
        send("GET", "/api/sensor-readings/" + reading.get("id").asLong(), f.token(), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.temperatureCelsius").value(-50)).andExpect(jsonPath("$.airHumidityPercent").value(100));
    }

    @ParameterizedTest
    @ValueSource(strings = {"INACTIVE", "FAULT"})
    void rejectsInactiveAndFaultySensors(String status) throws Exception {
        var f = fixture("produtor@teste.com", "TEMPERATURE");
        jdbc.update("UPDATE sensors SET status = ? WHERE id = ?", status, f.sensorId());
        em.clear();
        send("POST", "/api/sensor-readings", f.token(), body(f)).andExpect(status().isConflict());
        assertThat(outbox.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ownerId", "userId", "farmId", "cropAreaId", "status", "analysisStatus"})
    void rejectsApplicationControlledAndOwnerFields(String field) throws Exception {
        var f = fixture("produtor@teste.com", "TEMPERATURE");
        var b = body(f);
        b.put(field, "ANALYZED");
        send("POST", "/api/sensor-readings", f.token(), b).andExpect(status().isBadRequest());
    }

    @Test
    void listsAndLatestAreOrderedScopedAndPaginatedAndReadingsAreImmutable() throws Exception {
        var f = fixture("produtor@teste.com", "TEMPERATURE");
        var other = fixture("outro@teste.com", "TEMPERATURE");
        send("GET", "/api/crop-areas/" + f.cropId() + "/readings/latest", f.token(), null).andExpect(status().isNotFound());
        var b = body(f);
        b.put("measuredAt", "2026-01-01T00:00:00Z");
        var old = create(f, b);
        b.put("measuredAt", "2026-01-02T00:00:00Z");
        var newest = create(f, b);
        create(other, body(other));
        em.clear();
        var statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            send("GET", "/api/sensor-readings", f.token(), null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2));
            assertThat(statistics.getEntityFetchCount()).isZero();
        } finally { statistics.setStatisticsEnabled(false); }
        for (String path : List.of("/api/sensor-readings", "/api/sensors/" + f.sensorId() + "/readings",
                "/api/crop-areas/" + f.cropId() + "/readings")) {
            send("GET", path + "?page=0&size=1", f.token(), null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(newest.get("id").asLong()))
                    .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2));
            send("GET", path + "?page=1&size=1", f.token(), null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(old.get("id").asLong()));
        }
        send("GET", "/api/crop-areas/" + f.cropId() + "/readings/latest", f.token(), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(newest.get("id").asLong()));
        send("PUT", "/api/sensor-readings/" + old.get("id").asLong(), f.token(), body(f)).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void foreignAndMissingResourcesAreNotFound() throws Exception {
        var f = fixture("produtor@teste.com", "TEMPERATURE");
        var other = fixture("outro@teste.com", "TEMPERATURE");
        long id = create(other, body(other)).get("id").asLong();
        for (long sensorId : List.of(other.sensorId(), Long.MAX_VALUE)) {
            var b = body(f); b.put("sensorId", sensorId);
            send("POST", "/api/sensor-readings", f.token(), b).andExpect(status().isNotFound());
            send("GET", "/api/sensors/" + sensorId + "/readings", f.token(), null).andExpect(status().isNotFound());
        }
        for (long readingId : List.of(id, Long.MAX_VALUE)) {
            send("GET", "/api/sensor-readings/" + readingId, f.token(), null).andExpect(status().isNotFound());
            send("DELETE", "/api/sensor-readings/" + readingId, f.token(), null).andExpect(status().isNotFound());
        }
        for (long crop : List.of(other.cropId(), Long.MAX_VALUE)) {
            send("GET", "/api/crop-areas/" + crop + "/readings", f.token(), null).andExpect(status().isNotFound());
            send("GET", "/api/crop-areas/" + crop + "/readings/latest", f.token(), null).andExpect(status().isNotFound());
        }
    }

    @Test
    void allRoutesRequireTokenAndProducerMustRemainActive() throws Exception {
        var f = fixture("produtor@teste.com", "TEMPERATURE");
        for (String path : List.of("/api/sensor-readings", "/api/sensor-readings/1", "/api/sensors/1/readings",
                "/api/crop-areas/1/readings", "/api/crop-areas/1/readings/latest")) {
            send("GET", path, null, null).andExpect(status().isUnauthorized());
        }
        send("POST", "/api/sensor-readings", null, body(f)).andExpect(status().isUnauthorized());
        send("DELETE", "/api/sensor-readings/1", null, null).andExpect(status().isUnauthorized());
        jdbc.update("UPDATE users SET active = false WHERE id = ?", f.userId()); em.clear();
        send("POST", "/api/sensor-readings", f.token(), body(f)).andExpect(status().isForbidden());
        jdbc.update("UPDATE users SET active = true, role = 'TECHNICIAN' WHERE id = ?", f.userId()); em.clear();
        send("GET", "/api/sensor-readings", f.token(), null).andExpect(status().isForbidden());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void outboxInsertFailureRollsBackTheReading() throws Exception {
        var f = fixture("produtor@teste.com", "TEMPERATURE");
        doThrow(new IllegalStateException("Falha de persistência simulada")).when(outbox).saveAndFlush(any(OutboxEvent.class));
        try {
            send("POST", "/api/sensor-readings", f.token(), body(f)).andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Erro interno do servidor"));
            assertThat(readings.count()).isZero();
            assertThat(outbox.count()).isZero();
        } finally { reset(outbox); cleanup(f); }
    }

    @Test
    void consumptionIsIdempotentAndAnalyzedReadingsCannotBeDeleted() throws Exception {
        var f = fixture("produtor@teste.com", "TEMPERATURE");
        long id = create(f, body(f)).get("id").asLong();
        var event = mapper.readValue(outbox.findAll().getFirst().getPayload(), SensorReadingCreated.class);
        analysis.queue(event); em.clear();
        var first = readings.findById(id).orElseThrow();
        Instant updated = first.getUpdatedAt();
        assertThat(first.getAnalysisStatus().name()).isEqualTo("QUEUED_FOR_ANALYSIS");
        analysis.queue(event); em.clear();
        assertThat(readings.findById(id).orElseThrow().getUpdatedAt()).isEqualTo(updated);
        jdbc.update("UPDATE sensor_readings SET analysis_status = 'ANALYZED' WHERE id = ?", id); em.clear();
        analysis.queue(event);
        send("DELETE", "/api/sensor-readings/" + id, f.token(), null).andExpect(status().isConflict());
        assertThat(readings.existsById(id)).isTrue();
    }

    @Test
    void deletingReadingCancelsPendingEventAndSensorIsProtectedUntilThen() throws Exception {
        var f = fixture("produtor@teste.com", "TEMPERATURE");
        long id = create(f, body(f)).get("id").asLong();
        send("DELETE", "/api/sensors/" + f.sensorId(), f.token(), null).andExpect(status().isConflict());
        send("DELETE", "/api/sensor-readings/" + id, f.token(), null).andExpect(status().isNoContent());
        em.clear();
        assertThat(outbox.findAll().getFirst().getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(readings.existsById(id)).isFalse();
        send("DELETE", "/api/sensors/" + f.sensorId(), f.token(), null).andExpect(status().isNoContent());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void publicationFailureIsPersistedAndBackoffPreventsImmediateRetry() throws Exception {
        var f = fixture("failure-" + UUID.randomUUID() + "@teste.com", "TEMPERATURE");
        create(f, body(f));
        doThrow(new IllegalStateException("Broker indisponível")).when(sender).send(any());
        try {
            assertThat(publisher.publishPending()).isEqualTo(1);
            var event = outbox.findAll().getFirst();
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(event.getAttempts()).isEqualTo(1);
            assertThat(event.getLastError()).contains("IllegalStateException");
            assertThat(publisher.publishPending()).isZero();
            jdbc.update("UPDATE outbox_events SET next_attempt_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE event_id = ?", event.getEventId());
            assertThat(publisher.publishPending()).isEqualTo(1);
            assertThat(outbox.findById(event.getEventId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.FAILED);
            assertThat(publisher.publishPending()).isZero();
            assertThat(readings.count()).isEqualTo(1);
            assertThat(outbox.count()).isEqualTo(1);
        } finally { cleanup(f); }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void publishersSkipEventsAlreadyLockedByAnotherPublisher() throws Exception {
        var f = fixture("locks-" + UUID.randomUUID() + "@teste.com", "TEMPERATURE");
        create(f, body(f));
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(inv -> { entered.countDown(); if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout"); return null; })
                .when(sender).send(any());
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(publisher::publishPending);
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            try { assertThat(publisher.publishPending()).isZero(); } finally { release.countDown(); }
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(outbox.findAll().getFirst().getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
            verify(sender, times(1)).send(any());
        } finally { release.countDown(); cleanup(f); }
    }
}
