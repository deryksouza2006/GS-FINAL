package br.com.fiap.citizentech.sensor;

import br.com.fiap.citizentech.PostgresTestConfiguration;
import br.com.fiap.citizentech.auth.service.JwtService;
import br.com.fiap.citizentech.croparea.repository.CropAreaRepository;
import br.com.fiap.citizentech.sensor.repository.SensorRepository;
import br.com.fiap.citizentech.user.repository.UserRepository;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class SensorIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired EntityManager entityManager;
    @Autowired SensorRepository sensors;
    @Autowired CropAreaRepository cropAreas;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwtService;

    @Test
    void lifecyclePersistsDefaultsAndFieldsAndAllowsStatusChangesAndTransfer() throws Exception {
        String token = register("produtor@teste.com");
        JsonNode source = crop(token, "Talhao Origem");
        JsonNode target = crop(token, "Talhao Destino");
        LocalDate before = LocalDate.now();
        Map<String, Object> body = body(source.get("id").asLong(), "  dev-001  ");
        body.put("description", "Sensor instalado no solo");
        JsonNode created = create(token, body);
        long id = created.get("id").asLong();
        assertThat(created.size()).isEqualTo(13);
        assertThat(created.get("name").asString()).isEqualTo("Sensor Solo");
        assertThat(created.get("deviceCode").asString()).isEqualTo("DEV-001");
        assertThat(created.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(LocalDate.parse(created.get("installationDate").asString())).isIn(before, LocalDate.now());
        assertThat(created.get("cropAreaName").asString()).isEqualTo("Talhao Origem");
        assertThat(created.get("farmId").asLong()).isEqualTo(source.get("farmId").asLong());
        assertThat(created.get("farmName").asString()).isEqualTo("Fazenda Talhao Origem");
        entityManager.clear();
        Instant persistedCreatedAt = sensors.findById(id).orElseThrow().getCreatedAt();
        send("GET", "/api/sensors/" + id, token, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.sensorType").value("SOIL_MOISTURE"))
                .andExpect(jsonPath("$.deviceCode").value("DEV-001"))
                .andExpect(jsonPath("$.description").value("Sensor instalado no solo"));

        body = body(target.get("id").asLong(), "unused");
        body.remove("deviceCode");
        body.put("name", "Sensor Transferido");
        body.put("sensorType", "TEMPERATURE");
        body.put("status", "INACTIVE");
        LocalDate installed = LocalDate.now().minusDays(7);
        body.put("installationDate", installed.toString());
        body.put("description", "Após transferência");
        var result = send("PUT", "/api/sensors/" + id, token, body).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.deviceCode").value("DEV-001"))
                .andExpect(jsonPath("$.cropAreaId").value(target.get("id").asLong()))
                .andExpect(jsonPath("$.farmName").value("Fazenda Talhao Destino")).andReturn();
        JsonNode updated = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(Instant.parse(updated.get("createdAt").asString())).isEqualTo(persistedCreatedAt);
        assertThat(Instant.parse(updated.get("updatedAt").asString())).isAfter(persistedCreatedAt);
        entityManager.clear();
        var stored = sensors.findById(id).orElseThrow();
        assertThat(stored.getName()).isEqualTo("Sensor Transferido");
        assertThat(stored.getSensorType().name()).isEqualTo("TEMPERATURE");
        assertThat(stored.getInstallationDate()).isEqualTo(installed);
        assertThat(stored.getDescription()).isEqualTo("Após transferência");
        assertThat(stored.getCropArea().getId()).isEqualTo(target.get("id").asLong());

        body.remove("installationDate");
        body.remove("description");
        body.put("status", "FAULT");
        body.put("deviceCode", "  dev-002  ");
        send("PUT", "/api/sensors/" + id, token, body).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAULT"))
                .andExpect(jsonPath("$.deviceCode").value("DEV-002"))
                .andExpect(jsonPath("$.installationDate").value(installed.toString()));
        entityManager.clear();
        send("GET", "/api/sensors/" + id, token, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAULT"))
                .andExpect(jsonPath("$.description").isEmpty());
        send("DELETE", "/api/sensors/" + id, token, null)
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(sensors.existsById(id)).isFalse();
        send("GET", "/api/sensors/" + id, token, null).andExpect(status().isNotFound());
    }

    @Test
    void duplicateCodesAreCaseInsensitiveGloballyAndUpdateExcludesTheSameSensor() throws Exception {
        String token = register("produtor@teste.com");
        String other = register("outro@teste.com");
        long ownCrop = crop(token, "Meu Talhao").get("id").asLong();
        long otherCrop = crop(other, "Outro Talhao").get("id").asLong();
        long first = create(token, body(ownCrop, "dev-001")).get("id").asLong();
        send("POST", "/api/sensors", other, body(otherCrop, "  dEv-001  "))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Código de dispositivo já cadastrado"));
        long second = create(token, body(ownCrop, "dev-002")).get("id").asLong();
        send("PUT", "/api/sensors/" + second, token, body(ownCrop, "  DeV-001  "))
                .andExpect(status().isConflict());
        assertThat(sensors.findById(second).orElseThrow().getDeviceCode()).isEqualTo("DEV-002");
        send("PUT", "/api/sensors/" + first, token, body(ownCrop, " dev-001 "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deviceCode").value("DEV-001"));
    }

    @Test
    void listsAreScopedPaginatedAndLoadCropAreaAndFarmWithoutNPlusOne() throws Exception {
        String token = register("produtor@teste.com");
        String other = register("outro@teste.com");
        long firstCrop = crop(token, "Primeiro Talhao").get("id").asLong();
        long secondCrop = crop(token, "Segundo Talhao").get("id").asLong();
        long foreignCrop = crop(other, "Outro Talhao").get("id").asLong();
        send("GET", "/api/crop-areas/" + firstCrop + "/sensors", token, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        long first = create(token, body(firstCrop, "dev-001")).get("id").asLong();
        long second = create(token, body(firstCrop, "dev-002")).get("id").asLong();
        create(token, body(secondCrop, "dev-003"));
        create(other, body(foreignCrop, "dev-004"));
        entityManager.clear();
        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            send("GET", "/api/sensors", token, null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(3))
                    .andExpect(jsonPath("$.content[0].cropAreaName").value("Primeiro Talhao"))
                    .andExpect(jsonPath("$.content[2].farmName").value("Fazenda Segundo Talhao"))
                    .andExpect(jsonPath("$.totalElements").value(3));
            assertThat(statistics.getEntityFetchCount()).isZero();
        } finally {
            statistics.setStatisticsEnabled(false);
        }
        send("GET", "/api/sensors?page=0&size=1", token, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(first)).andExpect(jsonPath("$.totalPages").value(3));
        send("GET", "/api/crop-areas/" + firstCrop + "/sensors?page=1&size=1", token, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(second))
                .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2));
        send("GET", "/api/sensors", other, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void foreignAndMissingSensorsAndCropAreasReturnNotFoundAndCannotBeChanged() throws Exception {
        String token = register("produtor@teste.com");
        String other = register("outro@teste.com");
        long ownCrop = crop(token, "Meu Talhao").get("id").asLong();
        long foreignCrop = crop(other, "Outro Talhao").get("id").asLong();
        long ownSensor = create(token, body(ownCrop, "dev-001")).get("id").asLong();
        long foreignSensor = create(other, body(foreignCrop, "dev-002")).get("id").asLong();
        for (long cropId : List.of(foreignCrop, Long.MAX_VALUE)) {
            send("POST", "/api/sensors", token, body(cropId, "dev-new")).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Área de cultivo não encontrada"));
            send("PUT", "/api/sensors/" + ownSensor, token, body(cropId, "dev-001")).andExpect(status().isNotFound());
            send("GET", "/api/crop-areas/" + cropId + "/sensors", token, null).andExpect(status().isNotFound());
        }
        for (long id : List.of(foreignSensor, Long.MAX_VALUE)) {
            for (String method : List.of("GET", "PUT", "DELETE")) {
                send(method, "/api/sensors/" + id, token, body(ownCrop, "dev-new"))
                        .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Sensor não encontrado"));
            }
        }
        send("GET", "/api/sensors/" + ownSensor, token, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.cropAreaId").value(ownCrop)).andExpect(jsonPath("$.deviceCode").value("DEV-001"));
        assertThat(sensors.count()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"userId", "ownerId", "owner", "farmId"})
    void ownershipFieldsAreRejectedOnCreateAndUpdate(String field) throws Exception {
        String token = register("produtor@teste.com");
        long cropId = crop(token, "Meu Talhao").get("id").asLong();
        long id = create(token, body(cropId, "dev-001")).get("id").asLong();
        Map<String, Object> body = body(cropId, "dev-002");
        body.put(field, 123);
        send("POST", "/api/sensors", token, body).andExpect(status().isBadRequest());
        send("PUT", "/api/sensors/" + id, token, body).andExpect(status().isBadRequest());
        assertThat(sensors.count()).isEqualTo(1);
    }

    @Test
    void creationRejectsClientStatusEvenWhenActive() throws Exception {
        String token = register("produtor@teste.com");
        long cropId = crop(token, "Meu Talhao").get("id").asLong();
        Map<String, Object> body = body(cropId, "dev-001");
        body.put("status", "ACTIVE");
        send("POST", "/api/sensors", token, body).andExpect(status().isBadRequest());
        assertThat(sensors.count()).isZero();
    }

    @Test
    void validatesRequiredFieldsLengthsDatesAndEnumsOnCreateAndUpdate() throws Exception {
        String token = register("produtor@teste.com");
        long cropId = crop(token, "Meu Talhao").get("id").asLong();
        send("POST", "/api/sensors", token, Map.of()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.cropAreaId").exists()).andExpect(jsonPath("$.fields.name").exists())
                .andExpect(jsonPath("$.fields.deviceCode").exists()).andExpect(jsonPath("$.fields.sensorType").exists());
        Map<String, Object> invalid = body(cropId, "ab");
        invalid.put("name", "ab");
        send("POST", "/api/sensors", token, invalid).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").exists()).andExpect(jsonPath("$.fields.deviceCode").exists());
        long id = create(token, body(cropId, "dev-001")).get("id").asLong();
        invalid.put("cropAreaId", -1);
        invalid.put("name", "n".repeat(101));
        invalid.put("deviceCode", "d".repeat(81));
        invalid.put("description", "d".repeat(501));
        invalid.put("installationDate", LocalDate.now().plusDays(1).toString());
        for (String method : List.of("POST", "PUT")) {
            send(method, method.equals("POST") ? "/api/sensors" : "/api/sensors/" + id, token, invalid)
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Dados inválidos"))
                    .andExpect(jsonPath("$.fields.cropAreaId").exists()).andExpect(jsonPath("$.fields.name").exists())
                    .andExpect(jsonPath("$.fields.deviceCode").exists()).andExpect(jsonPath("$.fields.description").exists())
                    .andExpect(jsonPath("$.fields.installationDate").exists());
        }
        invalid = body(cropId, "   ");
        send("PUT", "/api/sensors/" + id, token, invalid).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.deviceCode").exists());
        invalid = body(cropId, "dev-new");
        invalid.put("sensorType", "UNKNOWN");
        send("POST", "/api/sensors", token, invalid).andExpect(status().isBadRequest());
        invalid = body(cropId, "dev-new");
        invalid.put("status", "UNKNOWN");
        send("PUT", "/api/sensors/" + id, token, invalid).andExpect(status().isBadRequest());
        assertThat(sensors.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"POST,/api/sensors", "GET,/api/sensors", "GET,/api/sensors/1",
            "PUT,/api/sensors/1", "DELETE,/api/sensors/1", "GET,/api/crop-areas/1/sensors"})
    void everyEndpointRequiresToken(String method, String path) throws Exception {
        send(method, path, null, body(1L, "dev-001")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401)).andExpect(jsonPath("$.path").value(path));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "TECHNICIAN"})
    void nonProducerRolesAndStaleProducerTokenAreRejected(String role) throws Exception {
        String original = register("produtor@teste.com");
        long cropId = crop(original, "Meu Talhao").get("id").asLong();
        long id = create(original, body(cropId, "dev-001")).get("id").asLong();
        jdbc.update("UPDATE users SET role = ? WHERE email = ?", role, "produtor@teste.com");
        entityManager.clear();
        String token = jwtService.issue(users.findByEmailIgnoreCase("produtor@teste.com").orElseThrow()).token();
        for (String method : List.of("POST", "GET", "PUT", "DELETE")) {
            send(method, method.equals("POST") ? "/api/sensors" : "/api/sensors/" + id,
                    token, body(cropId, "dev-002")).andExpect(status().isForbidden());
        }
        send("GET", "/api/sensors", token, null).andExpect(status().isForbidden());
        send("GET", "/api/crop-areas/" + cropId + "/sensors", token, null).andExpect(status().isForbidden());
        send("GET", "/api/sensors", original, null).andExpect(status().isForbidden());
    }

    @Test
    void disabledProducerCannotReadOrCreateWithAnExistingToken() throws Exception {
        String token = register("produtor@teste.com");
        long cropId = crop(token, "Meu Talhao").get("id").asLong();
        jdbc.update("UPDATE users SET active = false WHERE email = ?", "produtor@teste.com");
        entityManager.clear();
        send("GET", "/api/sensors", token, null).andExpect(status().isForbidden());
        send("POST", "/api/sensors", token, body(cropId, "dev-001")).andExpect(status().isForbidden());
    }

    @Test
    void cropAreaDeletionIsBlockedUntilItsSensorsAreDeletedWithoutCascade() throws Exception {
        String token = register("produtor@teste.com");
        String other = register("outro@teste.com");
        long cropId = crop(token, "Meu Talhao").get("id").asLong();
        long sensorId = create(token, body(cropId, "dev-001")).get("id").asLong();
        send("DELETE", "/api/crop-areas/" + cropId, other, null).andExpect(status().isNotFound());
        send("DELETE", "/api/crop-areas/" + cropId, token, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Não é possível excluir área de cultivo com sensores vinculados"));
        assertThat(cropAreas.existsById(cropId)).isTrue();
        assertThat(sensors.existsById(sensorId)).isTrue();
        send("DELETE", "/api/sensors/" + sensorId, token, null).andExpect(status().isNoContent());
        send("DELETE", "/api/crop-areas/" + cropId, token, null).andExpect(status().isNoContent());
    }

    @Test
    void invalidIdsAndPaginationUseTheExistingErrorFormat() throws Exception {
        String token = register("produtor@teste.com");
        long cropId = crop(token, "Meu Talhao").get("id").asLong();
        for (String path : List.of("/api/sensors/abc", "/api/sensors?page=-1", "/api/sensors?size=0",
                "/api/crop-areas/" + cropId + "/sensors?size=101")) {
            send("GET", path, token, null).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.timestamp").exists()).andExpect(jsonPath("$.status").value(400));
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRegistrationOfTheSameDeviceCodeReturnsCreatedAndConflict() throws Exception {
        String email = "concurrent-" + UUID.randomUUID() + "@teste.com";
        String token = register(email);
        JsonNode firstCrop = crop(token, "Concorrente A");
        JsonNode secondCrop = crop(token, "Concorrente B");
        String code = ("CONCURRENT-" + UUID.randomUUID()).toUpperCase(Locale.ROOT);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> firstTask = concurrentCreate(ready, start, token, firstCrop.get("id").asLong(), code);
            Callable<Integer> secondTask = concurrentCreate(ready, start, token, secondCrop.get("id").asLong(), code.toLowerCase());
            var first = executor.submit(firstTask);
            var second = executor.submit(secondTask);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM sensors WHERE upper(device_code) = ?", Long.class, code))
                    .isEqualTo(1L);
        } finally {
            start.countDown();
            jdbc.update("DELETE FROM sensors WHERE crop_area_id IN (?, ?)", firstCrop.get("id").asLong(), secondCrop.get("id").asLong());
            jdbc.update("DELETE FROM crop_areas WHERE id IN (?, ?)", firstCrop.get("id").asLong(), secondCrop.get("id").asLong());
            jdbc.update("DELETE FROM farms WHERE id IN (?, ?)", firstCrop.get("farmId").asLong(), secondCrop.get("farmId").asLong());
            users.deleteById(users.findByEmailIgnoreCase(email).orElseThrow().getId());
        }
    }

    private Callable<Integer> concurrentCreate(CountDownLatch ready, CountDownLatch start,
                                               String token, long cropId, String code) {
        return () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Tempo excedido ao iniciar requisições concorrentes");
            return send("POST", "/api/sensors", token, body(cropId, code)).andReturn().getResponse().getStatus();
        };
    }

    private String register(String email) throws Exception {
        var result = send("POST", "/api/auth/register", null,
                Map.of("name", "Produtor Teste", "email", email, "password", "12345678"))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("token").asString();
    }

    private JsonNode crop(String token, String name) throws Exception {
        var farm = send("POST", "/api/farms", token, Map.of("name", "Fazenda " + name, "city", "Campinas", "state", "SP",
                "totalArea", 100, "areaUnit", "HECTARE")).andExpect(status().isCreated()).andReturn();
        long farmId = mapper.readTree(farm.getResponse().getContentAsString()).get("id").asLong();
        var result = send("POST", "/api/crop-areas", token, Map.of("farmId", farmId, "name", name,
                "cropType", "Milho", "areaSize", 1, "areaUnit", "HECTARE")).andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode create(String token, Map<String, Object> body) throws Exception {
        var result = send("POST", "/api/sensors", token, body).andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private ResultActions send(String method, String path, String token, Object body) throws Exception {
        var builder = request(HttpMethod.valueOf(method), path);
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        return mvc.perform(builder);
    }

    private Map<String, Object> body(long cropId, String code) {
        return new LinkedHashMap<>(Map.of("cropAreaId", cropId, "name", "  Sensor Solo  ",
                "deviceCode", code, "sensorType", "SOIL_MOISTURE"));
    }
}
