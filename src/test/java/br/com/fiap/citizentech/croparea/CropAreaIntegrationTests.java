package br.com.fiap.citizentech.croparea;

import br.com.fiap.citizentech.PostgresTestConfiguration;
import br.com.fiap.citizentech.auth.service.JwtService;
import br.com.fiap.citizentech.croparea.repository.CropAreaRepository;
import br.com.fiap.citizentech.farm.domain.Farm;
import br.com.fiap.citizentech.farm.repository.FarmRepository;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
class CropAreaIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired EntityManager entityManager;
    @Autowired CropAreaRepository areas;
    @Autowired FarmRepository farms;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwtService;

    @Test
    void createGetUpdateAndDeletePersistAllFieldsAndPreserveMonitoringStatus() throws Exception {
        String token = register("produtor@teste.com");
        long farmId = farm(token, "Fazenda Sol", "100", "HECTARE");
        Map<String, Object> body = body(farmId, "1.25", "HECTARE");
        body.put("latitude", new BigDecimal("-22.900000"));
        body.put("longitude", new BigDecimal("-47.060000"));
        body.put("description", "Cultivo irrigado");
        JsonNode created = create(token, body);
        long id = created.get("id").asLong();
        assertThat(created.size()).isEqualTo(13);
        assertThat(created.get("farmId").asLong()).isEqualTo(farmId);
        assertThat(created.get("farmName").asString()).isEqualTo("Fazenda Sol");
        assertThat(created.get("name").asString()).isEqualTo("Talhão A");
        assertThat(created.get("cropType").asString()).isEqualTo("Milho");
        assertThat(created.get("status").asString()).isEqualTo("NORMAL");
        Instant createdAt = Instant.parse(created.get("createdAt").asString());
        entityManager.flush();
        entityManager.clear();
        Instant persistedCreatedAt = areas.findById(id).orElseThrow().getCreatedAt();
        send("GET", "/api/crop-areas/" + id, token, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.areaSize").value(1.25)).andExpect(jsonPath("$.areaUnit").value("HECTARE"))
                .andExpect(jsonPath("$.latitude").value(-22.9)).andExpect(jsonPath("$.longitude").value(-47.06))
                .andExpect(jsonPath("$.description").value("Cultivo irrigado"));
        jdbc.update("UPDATE crop_areas SET status = 'ATTENTION' WHERE id = ?", id);
        entityManager.clear();
        body.put("name", "Talhão Atualizado");
        body.put("cropType", "Soja");
        body.put("areaSize", new BigDecimal("15000"));
        body.put("areaUnit", "SQUARE_METER");
        body.put("latitude", null);
        body.put("longitude", null);
        body.put("description", null);
        var updatedResult = send("PUT", "/api/crop-areas/" + id, token, body)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ATTENTION")).andReturn();
        JsonNode updated = mapper.readTree(updatedResult.getResponse().getContentAsString());
        assertThat(Instant.parse(updated.get("createdAt").asString())).isEqualTo(persistedCreatedAt);
        assertThat(Instant.parse(updated.get("updatedAt").asString())).isAfter(createdAt);
        entityManager.clear();
        var stored = areas.findById(id).orElseThrow();
        assertThat(stored.getName()).isEqualTo("Talhão Atualizado");
        assertThat(stored.getCropType()).isEqualTo("Soja");
        assertThat(stored.getAreaSize()).isEqualByComparingTo("15000");
        assertThat(stored.getAreaUnit().name()).isEqualTo("SQUARE_METER");
        assertThat(stored.getLatitude()).isNull();
        assertThat(stored.getLongitude()).isNull();
        assertThat(stored.getDescription()).isNull();
        send("DELETE", "/api/crop-areas/" + id, token, null)
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(areas.existsById(id)).isFalse();
        send("GET", "/api/crop-areas/" + id, token, null).andExpect(status().isNotFound());
    }

    @Test
    void listsArePaginatedScopedAndFetchFarmNamesWithoutNPlusOne() throws Exception {
        String token = register("produtor@teste.com");
        String other = register("outro@teste.com");
        long firstFarm = farm(token, "Primeira Fazenda", "100", "HECTARE");
        long secondFarm = farm(token, "Segunda Fazenda", "100", "HECTARE");
        long otherFarm = farm(other, "Outra Fazenda", "100", "HECTARE");
        send("GET", "/api/farms/" + firstFarm + "/crop-areas", token, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        long first = create(token, body(firstFarm, "1", "HECTARE")).get("id").asLong();
        long second = create(token, body(firstFarm, "1", "HECTARE")).get("id").asLong();
        create(token, body(secondFarm, "1", "HECTARE"));
        create(other, body(otherFarm, "1", "HECTARE"));
        entityManager.clear();
        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            send("GET", "/api/crop-areas", token, null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(3))
                    .andExpect(jsonPath("$.content[0].farmName").value("Primeira Fazenda"))
                    .andExpect(jsonPath("$.content[2].farmName").value("Segunda Fazenda"))
                    .andExpect(jsonPath("$.totalElements").value(3));
            assertThat(statistics.getEntityFetchCount()).isZero();
        } finally {
            statistics.setStatisticsEnabled(false);
        }
        send("GET", "/api/crop-areas?page=0&size=1", token, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(first)).andExpect(jsonPath("$.totalPages").value(3));
        send("GET", "/api/farms/" + firstFarm + "/crop-areas?page=1&size=1", token, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(second))
                .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2));
        send("GET", "/api/crop-areas", other, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void moveToAnotherOwnedFarmChecksDestinationAndReleasesSourceCapacity() throws Exception {
        String token = register("produtor@teste.com");
        long source = farm(token, "Origem", "1", "HECTARE");
        long target = farm(token, "Destino", "10000", "SQUARE_METER");
        long id = create(token, body(source, "1", "HECTARE")).get("id").asLong();
        send("PUT", "/api/crop-areas/" + id, token, body(target, "10000", "SQUARE_METER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.farmId").value(target))
                .andExpect(jsonPath("$.farmName").value("Destino"));
        create(token, body(source, "1", "HECTARE"));
        send("POST", "/api/crop-areas", token, body(target, "0.01", "SQUARE_METER"))
                .andExpect(status().isConflict());
    }

    @Test
    void foreignAndMissingResourcesReturnNotFoundWithoutChangingAnything() throws Exception {
        String token = register("produtor@teste.com");
        String other = register("outro@teste.com");
        long ownFarm = farm(token, "Minha Fazenda", "100", "HECTARE");
        long foreignFarm = farm(other, "Outra Fazenda", "100", "HECTARE");
        JsonNode own = create(token, body(ownFarm, "1", "HECTARE"));
        long foreignArea = create(other, body(foreignFarm, "1", "HECTARE")).get("id").asLong();
        for (long farmId : List.of(foreignFarm, Long.MAX_VALUE)) {
            send("POST", "/api/crop-areas", token, body(farmId, "1", "HECTARE"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Propriedade não encontrada"));
            send("PUT", "/api/crop-areas/" + own.get("id").asLong(), token, body(farmId, "1", "HECTARE"))
                    .andExpect(status().isNotFound());
            send("GET", "/api/farms/" + farmId + "/crop-areas", token, null).andExpect(status().isNotFound());
        }
        for (long id : List.of(foreignArea, Long.MAX_VALUE)) {
            for (String method : List.of("GET", "PUT", "DELETE")) {
                send(method, "/api/crop-areas/" + id, token, body(ownFarm, "1", "HECTARE"))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.message").value("Área de cultivo não encontrada"));
            }
        }
        send("GET", "/api/crop-areas/" + own.get("id").asLong(), token, null)
                .andExpect(status().isOk()).andExpect(content().json(own.toString()));
        assertThat(areas.count()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ownerId", "userId", "owner", "status"})
    void ownershipAndStatusCannotBeSpecifiedOnCreateOrUpdate(String field) throws Exception {
        String token = register("produtor@teste.com");
        long farmId = farm(token, "Fazenda", "100", "HECTARE");
        long id = create(token, body(farmId, "1", "HECTARE")).get("id").asLong();
        Map<String, Object> body = body(farmId, "1", "HECTARE");
        body.put(field, field.equals("status") ? "CRITICAL" : 123);
        send("POST", "/api/crop-areas", token, body).andExpect(status().isBadRequest());
        send("PUT", "/api/crop-areas/" + id, token, body).andExpect(status().isBadRequest());
        assertThat(areas.count()).isEqualTo(1);
    }

    @Test
    void validatesRequiredFieldsLengthsAndNumericPrecision() throws Exception {
        String token = register("produtor@teste.com");
        long farmId = farm(token, "Fazenda", "100", "HECTARE");
        send("POST", "/api/crop-areas", token, Map.of()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.farmId").exists()).andExpect(jsonPath("$.fields.name").exists())
                .andExpect(jsonPath("$.fields.cropType").exists()).andExpect(jsonPath("$.fields.areaSize").exists())
                .andExpect(jsonPath("$.fields.areaUnit").exists());
        Map<String, Object> body = body(farmId, "0", "HECTARE");
        body.put("name", "ab");
        body.put("cropType", "a");
        send("POST", "/api/crop-areas", token, body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").exists()).andExpect(jsonPath("$.fields.cropType").exists())
                .andExpect(jsonPath("$.fields.areaSize").exists());
        long id = create(token, body(farmId, "1", "HECTARE")).get("id").asLong();
        body.put("name", "n".repeat(121));
        body.put("cropType", "c".repeat(81));
        body.put("areaSize", new BigDecimal("10000000000.001"));
        body.put("description", "d".repeat(501));
        send("PUT", "/api/crop-areas/" + id, token, body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").exists()).andExpect(jsonPath("$.fields.cropType").exists())
                .andExpect(jsonPath("$.fields.areaSize").exists()).andExpect(jsonPath("$.fields.description").exists());
        body = body(farmId, "1", "ACRE");
        send("POST", "/api/crop-areas", token, body).andExpect(status().isBadRequest());
        assertThat(areas.count()).isEqualTo(1);
    }

    @Test
    void coordinatesMustBePairedAndWithinLimitsOnCreateAndUpdate() throws Exception {
        String token = register("produtor@teste.com");
        long farmId = farm(token, "Fazenda", "100", "HECTARE");
        long id = create(token, body(farmId, "1", "HECTARE")).get("id").asLong();
        for (String coordinate : List.of("latitude", "longitude")) {
            Map<String, Object> body = body(farmId, "1", "HECTARE");
            body.put(coordinate, BigDecimal.ONE);
            send("POST", "/api/crop-areas", token, body).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.coordinatesComplete").value("Latitude e longitude devem ser informadas juntas"));
            send("PUT", "/api/crop-areas/" + id, token, body).andExpect(status().isBadRequest());
        }
        for (int sign : List.of(-1, 1)) {
            Map<String, Object> body = body(farmId, "1", "HECTARE");
            body.put("latitude", sign * 91);
            body.put("longitude", sign * 181);
            send("POST", "/api/crop-areas", token, body).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.latitude").exists()).andExpect(jsonPath("$.fields.longitude").exists());
        }
        Map<String, Object> boundary = body(farmId, "1", "HECTARE");
        boundary.put("latitude", -90);
        boundary.put("longitude", 180);
        create(token, boundary);
    }

    @ParameterizedTest
    @CsvSource({"HECTARE,1,SQUARE_METER,6000,4000,0.01", "SQUARE_METER,10000,HECTARE,0.60,0.40,0.01"})
    void capacityConvertsBothDirectionsAndExcludesPreviousValueOnUpdate(
            String farmUnit, String total, String cropUnit, String firstSize, String secondSize, String excess) throws Exception {
        String token = register("produtor@teste.com");
        long farmId = farm(token, "Fazenda", total, farmUnit);
        long first = create(token, body(farmId, firstSize, cropUnit)).get("id").asLong();
        create(token, body(farmId, secondSize, cropUnit));
        send("POST", "/api/crop-areas", token, body(farmId, excess, cropUnit))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message")
                        .value("A soma das áreas de cultivo ultrapassa a área total da propriedade"));
        send("PUT", "/api/crop-areas/" + first, token, body(farmId, firstSize, cropUnit)).andExpect(status().isOk());
        String increased = new BigDecimal(firstSize).add(new BigDecimal(excess)).toPlainString();
        send("PUT", "/api/crop-areas/" + first, token, body(farmId, increased, cropUnit)).andExpect(status().isConflict());
        assertThat(areas.findById(first).orElseThrow().getAreaSize()).isEqualByComparingTo(firstSize);
        Map<String, Object> shrink = Map.of("name", "Fazenda", "city", "Campinas", "state", "SP",
                "totalArea", new BigDecimal(total).multiply(new BigDecimal("0.9")), "areaUnit", farmUnit);
        send("PUT", "/api/farms/" + farmId, token, shrink).andExpect(status().isConflict());
    }

    @Test
    void transferToFullFarmIsRejectedAndDeletingAnAreaReleasesCapacity() throws Exception {
        String token = register("produtor@teste.com");
        long source = farm(token, "Origem", "1", "HECTARE");
        long target = farm(token, "Destino", "1", "HECTARE");
        long sourceArea = create(token, body(source, "1", "HECTARE")).get("id").asLong();
        long targetArea = create(token, body(target, "1", "HECTARE")).get("id").asLong();
        send("PUT", "/api/crop-areas/" + sourceArea, token, body(target, "1", "HECTARE"))
                .andExpect(status().isConflict());
        assertThat(areas.findById(sourceArea).orElseThrow().getFarm().getId()).isEqualTo(source);
        send("DELETE", "/api/crop-areas/" + targetArea, token, null).andExpect(status().isNoContent());
        send("PUT", "/api/crop-areas/" + sourceArea, token, body(target, "1", "HECTARE"))
                .andExpect(status().isOk());
    }

    @Test
    void legacyFarmWithoutTotalAreaDoesNotApplyAnInventedLimit() throws Exception {
        String token = register("produtor@teste.com");
        var owner = users.findByEmailIgnoreCase("produtor@teste.com").orElseThrow();
        Farm farm = farms.saveAndFlush(new Farm(owner, "Antiga", owner.getName(), "Campinas", "SP", "Brasil", null, null));
        create(token, body(farm.getId(), "9999999999.99", "HECTARE"));
    }

    @ParameterizedTest
    @CsvSource({"POST,/api/crop-areas", "GET,/api/crop-areas", "GET,/api/crop-areas/1",
            "PUT,/api/crop-areas/1", "DELETE,/api/crop-areas/1", "GET,/api/farms/1/crop-areas"})
    void everyEndpointRequiresToken(String method, String path) throws Exception {
        send(method, path, null, body(1L, "1", "HECTARE")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401)).andExpect(jsonPath("$.path").value(path));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "TECHNICIAN"})
    void nonProducerRolesAndStaleProducerTokenAreRejected(String role) throws Exception {
        String original = register("produtor@teste.com");
        long farmId = farm(original, "Fazenda", "100", "HECTARE");
        long id = create(original, body(farmId, "1", "HECTARE")).get("id").asLong();
        jdbc.update("UPDATE users SET role = ? WHERE email = ?", role, "produtor@teste.com");
        entityManager.clear();
        String token = jwtService.issue(users.findByEmailIgnoreCase("produtor@teste.com").orElseThrow()).token();
        for (String method : List.of("POST", "GET", "PUT", "DELETE")) {
            send(method, method.equals("POST") ? "/api/crop-areas" : "/api/crop-areas/" + id,
                    token, body(farmId, "1", "HECTARE")).andExpect(status().isForbidden());
        }
        send("GET", "/api/crop-areas", token, null).andExpect(status().isForbidden());
        send("GET", "/api/farms/" + farmId + "/crop-areas", token, null).andExpect(status().isForbidden());
        send("GET", "/api/crop-areas", original, null).andExpect(status().isForbidden());
    }

    @Test
    void disabledProducerCannotReadOrCreateUsingAnExistingToken() throws Exception {
        String token = register("produtor@teste.com");
        long farmId = farm(token, "Fazenda", "100", "HECTARE");
        jdbc.update("UPDATE users SET active = false WHERE email = ?", "produtor@teste.com");
        entityManager.clear();
        send("GET", "/api/crop-areas", token, null).andExpect(status().isForbidden());
        send("POST", "/api/crop-areas", token, body(farmId, "1", "HECTARE")).andExpect(status().isForbidden());
    }

    @Test
    void invalidIdsAndPaginationUseTheExistingErrorFormat() throws Exception {
        String token = register("produtor@teste.com");
        long farmId = farm(token, "Fazenda", "100", "HECTARE");
        for (String path : List.of("/api/crop-areas/abc", "/api/crop-areas?page=-1", "/api/crop-areas?size=0",
                "/api/farms/" + farmId + "/crop-areas?size=101")) {
            send("GET", path, token, null).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.timestamp").exists()).andExpect(jsonPath("$.status").value(400));
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentCreatesCannotOverbookTheFarm() throws Exception {
        String email = "concurrent-" + UUID.randomUUID() + "@teste.com";
        String token = register(email);
        long farmId = farm(token, "Concorrente", "1", "HECTARE");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var task = (java.util.concurrent.Callable<Integer>) () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Tempo excedido ao iniciar requisições concorrentes");
                }
                return send("POST", "/api/crop-areas", token, body(farmId, "0.75", "HECTARE"))
                        .andReturn().getResponse().getStatus();
            };
            var first = executor.submit(task);
            var second = executor.submit(task);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(areas.sumSquareMeters(farmId, null)).isEqualByComparingTo("7500");
        } finally {
            start.countDown();
            jdbc.update("DELETE FROM crop_areas WHERE farm_id = ?", farmId);
            farms.deleteById(farmId);
            users.deleteById(users.findByEmailIgnoreCase(email).orElseThrow().getId());
        }
    }

    private String register(String email) throws Exception {
        var result = send("POST", "/api/auth/register", null,
                Map.of("name", "Produtor Teste", "email", email, "password", "12345678"))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("token").asString();
    }

    private long farm(String token, String name, String total, String unit) throws Exception {
        var result = send("POST", "/api/farms", token, Map.of("name", name, "city", "Campinas", "state", "SP",
                "totalArea", new BigDecimal(total), "areaUnit", unit)).andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode create(String token, Map<String, Object> body) throws Exception {
        var result = send("POST", "/api/crop-areas", token, body).andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private ResultActions send(String method, String path, String token, Object body) throws Exception {
        var builder = request(HttpMethod.valueOf(method), path);
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        return mvc.perform(builder);
    }

    private Map<String, Object> body(long farmId, String size, String unit) {
        return new LinkedHashMap<>(Map.of("farmId", farmId, "name", "  Talhão A  ", "cropType", "  Milho  ",
                "areaSize", new BigDecimal(size), "areaUnit", unit));
    }
}
