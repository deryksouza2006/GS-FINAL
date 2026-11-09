package br.com.fiap.citizentech.auth;

import br.com.fiap.citizentech.auth.config.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class JwtPropertiesTests {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class)
            .withPropertyValues("app.security.jwt.issuer=citizentech-api",
                    "app.security.jwt.expiration-minutes=120");

    @Test
    void rejectsShortSecretAtStartup() {
        context.withPropertyValues("app.security.jwt.secret=too-short")
                .run(result -> assertThat(result).hasFailed());
    }

    @Test
    void rejectsMissingSecretAtStartup() {
        context.run(result -> assertThat(result).hasFailed());
    }

    @Test
    void rejectsNonPositiveExpirationAtStartup() {
        context.withPropertyValues("app.security.jwt.secret=" + "x".repeat(32),
                        "app.security.jwt.expiration-minutes=0")
                .run(result -> assertThat(result).hasFailed());
    }

    @Test
    void acceptsMinimumSecretLength() {
        context.withPropertyValues("app.security.jwt.secret=" + "x".repeat(32))
                .run(result -> assertThat(result).hasNotFailed().hasSingleBean(JwtProperties.class));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JwtProperties.class)
    static class PropertiesConfiguration {
    }
}
