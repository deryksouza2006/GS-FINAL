package br.com.fiap.citizentech.sensorreading;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public abstract class ReadingTestSupport {
    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected JdbcTemplate jdbc;

    protected record Fixture(String token, long userId, long farmId, long cropId, long sensorId) { }

    protected Fixture fixture(String email, String type) throws Exception {
        JsonNode user = json(send("POST", "/api/auth/register", null,
                Map.of("name", "Produtor Teste", "email", email, "password", "12345678")).andExpect(status().isCreated()));
        String token = user.get("token").asString();
        long farm = json(send("POST", "/api/farms", token, Map.of("name", "Fazenda Sol", "city", "Campinas", "state", "SP",
                "totalArea", 100, "areaUnit", "HECTARE")).andExpect(status().isCreated())).get("id").asLong();
        long crop = json(send("POST", "/api/crop-areas", token, Map.of("farmId", farm, "name", "Talhao A",
                "cropType", "Milho", "areaSize", 1, "areaUnit", "HECTARE")).andExpect(status().isCreated())).get("id").asLong();
        long sensor = json(send("POST", "/api/sensors", token, Map.of("cropAreaId", crop, "name", "Sensor Teste",
                "deviceCode", email, "sensorType", type)).andExpect(status().isCreated())).get("id").asLong();
        return new Fixture(token, user.get("user").get("id").asLong(), farm, crop, sensor);
    }

    protected Map<String, Object> body(Fixture f) {
        return new LinkedHashMap<>(Map.of("sensorId", f.sensorId(), "temperatureCelsius", 25,
                "airHumidityPercent", 60, "soilMoisturePercent", 40, "batteryPercent", 90));
    }

    protected JsonNode create(Fixture f, Map<String, Object> body) throws Exception {
        return json(send("POST", "/api/sensor-readings", f.token(), body).andExpect(status().isCreated()));
    }

    protected JsonNode json(ResultActions action) throws Exception {
        return mapper.readTree(action.andReturn().getResponse().getContentAsString());
    }

    protected ResultActions send(String method, String path, String token, Object body) throws Exception {
        var builder = request(HttpMethod.valueOf(method), path);
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        return mvc.perform(builder);
    }

    protected void cleanup(Fixture f) {
        jdbc.update("DELETE FROM outbox_events WHERE aggregate_type = 'SensorReading' AND aggregate_id IN (SELECT id FROM sensor_readings WHERE sensor_id = ?)", f.sensorId());
        jdbc.update("DELETE FROM sensor_readings WHERE sensor_id = ?", f.sensorId());
        jdbc.update("DELETE FROM sensors WHERE id = ?", f.sensorId());
        jdbc.update("DELETE FROM crop_areas WHERE id = ?", f.cropId());
        jdbc.update("DELETE FROM farms WHERE id = ?", f.farmId());
        jdbc.update("DELETE FROM users WHERE id = ?", f.userId());
    }
}
