package br.com.fiap.citizentech.auth;

import br.com.fiap.citizentech.PostgresTestConfiguration;
import br.com.fiap.citizentech.auth.config.JwtProperties;
import br.com.fiap.citizentech.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class AuthIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtDecoder decoder;
    @Autowired JwtEncoder encoder;
    @Autowired JwtProperties properties;
    @Autowired JwtAuthenticationConverter converter;

    private static final String EMAIL = "produtor@teste.com";
    private static final String PASSWORD = "12345678";

    @Test
    void registerNormalizesEmailHashesPasswordAndIssuesProducerJwt() throws Exception {
        JsonNode body = register("  Produtor@TESTE.com  ", PASSWORD);
        assertThat(body.get("tokenType").asString()).isEqualTo("Bearer");
        assertThat(body.get("user").get("email").asString()).isEqualTo(EMAIL);
        assertThat(body.get("user").get("role").asString()).isEqualTo("PRODUCER");
        assertThat(body.get("user").has("passwordHash")).isFalse();
        assertThat(body.get("user").has("password")).isFalse();
        var user = users.findByEmailIgnoreCase(EMAIL).orElseThrow();
        assertThat(user.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwords.matches(PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(user.getUsername()).isEqualTo(EMAIL);
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getAuthorities()).extracting("authority").containsExactly("ROLE_PRODUCER");
        Jwt jwt = decoder.decode(body.get("token").asString());
        assertThat(jwt.getHeaders().get("alg")).isEqualTo("HS256");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(properties.issuer());
        assertThat(jwt.getSubject()).isEqualTo(EMAIL);
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isEqualTo(Instant.parse(body.get("expiresAt").asString()));
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(properties.expirationMinutes() * 60));
        assertThat(((Number) jwt.getClaim("userId")).longValue()).isEqualTo(user.getId());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("PRODUCER");
        assertThat(converter.convert(jwt).getAuthorities()).extracting("authority").contains("ROLE_PRODUCER");
    }

    @Test
    void duplicateEmailReturnsConflict() throws Exception {
        register(EMAIL, PASSWORD);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration("  PRODUTOR@TESTE.COM  ", PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("E-mail já cadastrado"))
                .andExpect(jsonPath("$.path").value("/api/auth/register"));
        assertThat(users.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "TECHNICIAN", "PRODUCER"})
    void registerRejectsRoleField(String role) throws Exception {
        String json = mapper.writeValueAsString(Map.of("name", "Produtor Teste", "email", EMAIL,
                "password", PASSWORD, "role", role));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
        assertThat(users.existsByEmailIgnoreCase(EMAIL)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1234567", "                                                                         "})
    void registerRejectsInvalidPassword(String password) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration(EMAIL, password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.password").exists());
    }

    @Test
    void registrationValidatesRequiredNameAndEmail() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("name", " ", "email", "invalid", "password", PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").exists())
                .andExpect(jsonPath("$.fields.email").exists());
    }

    @Test
    void registrationValidatesMaximumLengths() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("name", "n".repeat(101),
                                "email", "a".repeat(145) + "@test.com", "password", "p".repeat(73)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").exists())
                .andExpect(jsonPath("$.fields.email").exists())
                .andExpect(jsonPath("$.fields.password").exists());
    }

    @Test
    void supportsSeventyTwoUnicodePasswordCharacters() throws Exception {
        String password = "á".repeat(72);
        register(EMAIL, password);
        login("  PRODUTOR@TESTE.COM  ", password, 200);
    }

    @Test
    void validLoginReturnsTokenAndUser() throws Exception {
        register(EMAIL, PASSWORD);
        JsonNode body = login("  PRODUTOR@TESTE.COM  ", PASSWORD, 200);
        assertThat(decoder.decode(body.get("token").asString()).getSubject()).isEqualTo(EMAIL);
        assertThat(body.get("user").get("role").asString()).isEqualTo("PRODUCER");
        assertThat(body.get("user").has("passwordHash")).isFalse();
    }

    @Test
    void incorrectPasswordReturnsUnauthorized() throws Exception {
        register(EMAIL, PASSWORD);
        JsonNode body = login(EMAIL, "wrong-password", 401);
        assertThat(body.get("message").asString()).isEqualTo("Credenciais inválidas");
    }

    @Test
    void unknownUserReturnsSameUnauthorizedResponse() throws Exception {
        JsonNode body = login(EMAIL, PASSWORD, 401);
        assertThat(body.get("message").asString()).isEqualTo("Credenciais inválidas");
    }

    @Test
    void disabledUserCannotLogin() throws Exception {
        register(EMAIL, PASSWORD);
        // Atualização real no banco para testar isEnabled sem adicionar API administrativa.
        var user = users.findByEmailIgnoreCase(EMAIL).orElseThrow();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "active", false);
        users.saveAndFlush(user);
        login(EMAIL, PASSWORD, 401);
    }

    @Test
    void meWithoutTokenReturnsStandardUnauthorizedResponse() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/api/auth/me"));
    }

    @Test
    void meWithTokenReturnsDatabaseUserWithoutPasswordOrSession() throws Exception {
        JsonNode auth = register(EMAIL, PASSWORD);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + auth.get("token").asString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(auth.get("user").get("id").asLong()))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("PRODUCER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());
    }

    @Test
    void meForDeletedUserReturnsNotFound() throws Exception {
        JsonNode auth = register(EMAIL, PASSWORD);
        users.deleteAll();
        users.flush();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + auth.get("token").asString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Usuário não encontrado"));
    }

    @Test
    void expiredTokenReturnsUnauthorized() throws Exception {
        assertUnauthorized(signedToken(properties.issuer(), Instant.now().minusSeconds(3600), MacAlgorithm.HS256));
    }

    @Test
    void wrongIssuerReturnsUnauthorized() throws Exception {
        assertUnauthorized(signedToken("another-issuer", Instant.now().plusSeconds(3600), MacAlgorithm.HS256));
    }

    @Test
    void malformedTokenReturnsUnauthorized() throws Exception {
        assertUnauthorized("invalid.token.value");
    }

    @Test
    void tamperedSignatureReturnsUnauthorized() throws Exception {
        String token = register(EMAIL, PASSWORD).get("token").asString();
        String[] parts = token.split("\\.");
        parts[2] = (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        assertUnauthorized(String.join(".", parts));
    }

    @Test
    void otherRoutesRequireJwtAndHealthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/register")).andExpect(status().isUnauthorized());
        mvc.perform(get("/unknown")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());
    }

    private void assertUnauthorized(String token) throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Token ausente ou inválido"));
    }

    private String signedToken(String issuer, Instant expiresAt, MacAlgorithm algorithm) {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(EMAIL)
                .issuedAt(expiresAt.minusSeconds(7200)).expiresAt(expiresAt)
                .claim("userId", 1L).claim("roles", List.of("PRODUCER")).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(algorithm).build(), claims)).getTokenValue();
    }

    private JsonNode register(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration(email, password)))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode login(String email, String password, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().is(expectedStatus)).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private String registration(String email, String password) {
        return mapper.writeValueAsString(Map.of("name", "Produtor Teste", "email", email, "password", password));
    }
}
