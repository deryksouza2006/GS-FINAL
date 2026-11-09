package br.com.fiap.citizentech.farm;

import br.com.fiap.citizentech.PostgresTestConfiguration;
import br.com.fiap.citizentech.auth.service.JwtService;
import br.com.fiap.citizentech.croparea.domain.AreaUnit;
import br.com.fiap.citizentech.croparea.domain.CropArea;
import br.com.fiap.citizentech.croparea.repository.CropAreaRepository;
import br.com.fiap.citizentech.farm.domain.Farm;
import br.com.fiap.citizentech.farm.repository.FarmRepository;
import br.com.fiap.citizentech.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
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
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class FarmIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired FarmRepository farms;
    @Autowired CropAreaRepository cropAreas;
    @Autowired UserRepository users;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwtService;

    @Test
    void producerCanCreateReadUpdateAndDeleteFarm() throws Exception {
        String token = register("produtor@teste.com");
        JsonNode created = create(token);
        long id = created.get("id").asLong();
        assertThat(created.size()).isEqualTo(6);
        assertThat(created.get("name").asString()).isEqualTo("Fazenda Sol");
        assertThat(created.get("city").asString()).isEqualTo("Campinas");
        assertThat(created.get("state").asString()).isEqualTo("SP");
        assertThat(created.get("totalArea").decimalValue()).isEqualByComparingTo("25.50");
        assertThat(created.get("areaUnit").asString()).isEqualTo("HECTARE");
        var stored = farms.findById(id).orElseThrow();
        assertThat(stored.getOwner().getEmail()).isEqualTo("produtor@teste.com");
        assertThat(stored.getResponsibleName()).isEqualTo("Produtor Teste");
        assertThat(stored.getCountry()).isEqualTo("Brasil");
        send("GET", "/api/farms/" + id, token, null)
                .andExpect(status().isOk()).andExpect(content().json(created.toString()));

        Map<String, Object> update = validBody();
        update.put("name", "Fazenda Lua");
        update.put("city", "Londrina");
        update.put("state", "PR");
        update.put("totalArea", new BigDecimal("1000.25"));
        update.put("areaUnit", "SQUARE_METER");
        send("PUT", "/api/farms/" + id, token, update).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.name").value("Fazenda Lua"));
        entityManager.flush();
        entityManager.clear();
        send("GET", "/api/farms/" + id, token, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Fazenda Lua"))
                .andExpect(jsonPath("$.city").value("Londrina"))
                .andExpect(jsonPath("$.state").value("PR"))
                .andExpect(jsonPath("$.totalArea").value(1000.25))
                .andExpect(jsonPath("$.areaUnit").value("SQUARE_METER"));
        send("DELETE", "/api/farms/" + id, token, null)
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(farms.existsById(id)).isFalse();
        send("GET", "/api/farms/" + id, token, null).andExpect(status().isNotFound());
    }

    @Test
    void listIsPaginatedAndContainsOnlyTheCurrentProducersFarms() throws Exception {
        String first = register("primeiro@teste.com");
        String second = register("segundo@teste.com");
        send("GET", "/api/farms", second, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.totalElements").value(0));
        long firstId = create(first).get("id").asLong();
        long secondId = create(first).get("id").asLong();
        long foreignId = create(second).get("id").asLong();
        send("GET", "/api/farms?page=0&size=1", first, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(firstId))
                .andExpect(jsonPath("$.content[0].owner").doesNotExist())
                .andExpect(jsonPath("$.content[0].email").doesNotExist())
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2));
        send("GET", "/api/farms?page=1&size=1", first, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(secondId));
        send("GET", "/api/farms", second, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(foreignId))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void anotherProducerCannotReadChangeOrDeleteFarm() throws Exception {
        String owner = register("dono@teste.com");
        String stranger = register("outro@teste.com");
        JsonNode original = create(owner);
        String path = "/api/farms/" + original.get("id").asLong();
        for (String method : List.of("GET", "PUT", "DELETE")) {
            send(method, path, stranger, method.equals("PUT") ? validBody() : null)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Propriedade não encontrada"))
                    .andExpect(jsonPath("$.path").value(path));
        }
        send("GET", path, owner, null).andExpect(status().isOk())
                .andExpect(content().json(original.toString()));
        assertThat(farms.findById(original.get("id").asLong()).orElseThrow().getOwner().getEmail())
                .isEqualTo("dono@teste.com");
    }

    @Test
    void missingFarmReturnsTheSameNotFoundForReadUpdateAndDelete() throws Exception {
        String token = register("produtor@teste.com");
        for (String method : List.of("GET", "PUT", "DELETE")) {
            send(method, "/api/farms/9223372036854775807", token, method.equals("PUT") ? validBody() : null)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Propriedade não encontrada"));
        }
    }

    @ParameterizedTest
    @CsvSource({"POST,/api/farms", "GET,/api/farms", "GET,/api/farms/1", "PUT,/api/farms/1", "DELETE,/api/farms/1"})
    void everyOperationRequiresToken(String method, String path) throws Exception {
        send(method, path, null, validBody()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401)).andExpect(jsonPath("$.path").value(path));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "TECHNICIAN"})
    void nonProducerRolesCannotUseAnyFarmOperation(String role) throws Exception {
        String producerToken = register("usuario@teste.com");
        long id = create(producerToken).get("id").asLong();
        jdbc.update("UPDATE users SET role = ? WHERE email = ?", role, "usuario@teste.com");
        entityManager.clear();
        String token = jwtService.issue(users.findByEmailIgnoreCase("usuario@teste.com").orElseThrow()).token();
        for (String method : List.of("POST", "GET", "PUT", "DELETE")) {
            String path = method.equals("POST") ? "/api/farms" : "/api/farms/" + id;
            send(method, path, token, validBody()).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }
        send("GET", "/api/farms", token, null).andExpect(status().isForbidden());
        // O token antigo ainda contém PRODUCER, mas a regra também consulta o banco.
        send("GET", "/api/farms", producerToken, null).andExpect(status().isForbidden());
    }

    @Test
    void disabledProducerIsRejectedEvenWithPreviouslyIssuedToken() throws Exception {
        String token = register("produtor@teste.com");
        jdbc.update("UPDATE users SET active = false WHERE email = ?", "produtor@teste.com");
        entityManager.clear();
        send("POST", "/api/farms", token, validBody()).andExpect(status().isForbidden());
        assertThat(farms.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"userId", "ownerId"})
    void requestCannotSpecifyOwnershipOnCreateOrUpdate(String field) throws Exception {
        String token = register("produtor@teste.com");
        String otherToken = register("outro@teste.com");
        long id = create(token).get("id").asLong();
        long otherId = users.findByEmailIgnoreCase("outro@teste.com").orElseThrow().getId();
        Map<String, Object> body = validBody();
        body.put(field, otherId);
        send("POST", "/api/farms", token, body).andExpect(status().isBadRequest());
        send("PUT", "/api/farms/" + id, token, body).andExpect(status().isBadRequest());
        assertThat(farms.count()).isEqualTo(1);
        send("GET", "/api/farms", otherToken, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void invalidCreateAndUpdateReturnFieldErrorsWithoutChangingStoredFarm() throws Exception {
        String token = register("produtor@teste.com");
        JsonNode original = create(token);
        Map<String, Object> body = validBody();
        body.put("name", "ab");
        body.put("city", " ");
        body.put("state", "ZZ");
        body.put("totalArea", BigDecimal.ZERO);
        body.put("areaUnit", null);
        for (String method : List.of("POST", "PUT")) {
            String path = method.equals("POST") ? "/api/farms" : "/api/farms/" + original.get("id").asLong();
            send(method, path, token, body).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Dados inválidos"))
                    .andExpect(jsonPath("$.fields.name").exists())
                    .andExpect(jsonPath("$.fields.city").exists())
                    .andExpect(jsonPath("$.fields.state").exists())
                    .andExpect(jsonPath("$.fields.totalArea").exists())
                    .andExpect(jsonPath("$.fields.areaUnit").exists());
        }
        assertThat(farms.count()).isEqualTo(1);
        send("GET", "/api/farms/" + original.get("id").asLong(), token, null)
                .andExpect(status().isOk()).andExpect(content().json(original.toString()));
    }

    @Test
    void requiredFieldsAndDatabaseLengthAndPrecisionLimitsAreValidated() throws Exception {
        String token = register("produtor@teste.com");
        send("POST", "/api/farms", token, Map.of()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").exists()).andExpect(jsonPath("$.fields.city").exists())
                .andExpect(jsonPath("$.fields.state").exists()).andExpect(jsonPath("$.fields.totalArea").exists())
                .andExpect(jsonPath("$.fields.areaUnit").exists());
        Map<String, Object> body = validBody();
        body.put("name", "n".repeat(101));
        body.put("city", "c".repeat(101));
        body.put("state", "SAO");
        body.put("totalArea", new BigDecimal("10000000000.001"));
        send("POST", "/api/farms", token, body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").exists()).andExpect(jsonPath("$.fields.city").exists())
                .andExpect(jsonPath("$.fields.state").exists()).andExpect(jsonPath("$.fields.totalArea").exists());
        body = validBody();
        body.put("areaUnit", "ACRE");
        send("POST", "/api/farms", token, body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        assertThat(farms.count()).isZero();
    }

    @Test
    void invalidIdAndPaginationReturnStandardBadRequest() throws Exception {
        String token = register("produtor@teste.com");
        for (String path : List.of("/api/farms/abc", "/api/farms?page=-1", "/api/farms?size=0",
                "/api/farms?size=101", "/api/farms?page=invalid")) {
            send("GET", path, token, null).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.timestamp").exists()).andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"));
        }
    }

    @Test
    void legacyFarmCanBeUpdatedWithoutLosingExistingMetadata() throws Exception {
        String token = register("produtor@teste.com");
        var owner = users.findByEmailIgnoreCase("produtor@teste.com").orElseThrow();
        Farm legacy = farms.saveAndFlush(new Farm(owner, "Propriedade Antiga", "Responsável Original",
                "Campinas", "SP", "Brasil", new BigDecimal("-22.900000"), new BigDecimal("-47.060000")));
        long id = legacy.getId();
        send("GET", "/api/farms/" + id, token, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalArea").isEmpty()).andExpect(jsonPath("$.areaUnit").isEmpty());
        send("PUT", "/api/farms/" + id, token, validBody()).andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
        Farm updated = farms.findById(id).orElseThrow();
        assertThat(updated.getResponsibleName()).isEqualTo("Responsável Original");
        assertThat(updated.getCountry()).isEqualTo("Brasil");
        assertThat(updated.getLatitude()).isEqualByComparingTo("-22.900000");
        assertThat(updated.getLongitude()).isEqualByComparingTo("-47.060000");
        assertThat(updated.getOwner().getId()).isEqualTo(owner.getId());
        assertThat(updated.getTotalArea()).isEqualByComparingTo("25.50");
    }

    @Test
    void deletingFarmWithCropAreaReturnsConflictAndPreservesBothRecords() throws Exception {
        String token = register("produtor@teste.com");
        long farmId = create(token).get("id").asLong();
        Farm farm = farms.findById(farmId).orElseThrow();
        CropArea crop = cropAreas.saveAndFlush(new CropArea(farm, "Talhão A", "Milho", BigDecimal.ONE,
                AreaUnit.HECTARE, null, null, null));
        send("DELETE", "/api/farms/" + farmId, token, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Não é possível excluir propriedade com áreas de cultivo vinculadas"))
                .andExpect(jsonPath("$.status").value(409));
        assertThat(farms.existsById(farmId)).isTrue();
        assertThat(cropAreas.existsById(crop.getId())).isTrue();
    }

    private String register(String email) throws Exception {
        var result = send("POST", "/api/auth/register", null,
                Map.of("name", "Produtor Teste", "email", email, "password", "12345678"))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("token").asString();
    }

    private JsonNode create(String token) throws Exception {
        var result = send("POST", "/api/farms", token, validBody())
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private ResultActions send(String method, String path, String token, Object body) throws Exception {
        var builder = request(HttpMethod.valueOf(method), path);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        }
        return mvc.perform(builder);
    }

    private Map<String, Object> validBody() {
        return new LinkedHashMap<>(Map.of("name", "  Fazenda Sol  ", "city", "  Campinas  ", "state", " sp ",
                "totalArea", new BigDecimal("25.50"), "areaUnit", "HECTARE"));
    }
}
