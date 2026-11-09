package br.com.fiap.citizentech;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class CitizentechApiApplicationTests {

    @Test
    void contextLoads() {
    }
}
