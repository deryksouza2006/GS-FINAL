package br.com.fiap.citizentech.analysis;

import br.com.fiap.citizentech.PostgresTestConfiguration;
import br.com.fiap.citizentech.analysis.ai.*;
import br.com.fiap.citizentech.analysis.rag.AgronomyKnowledge;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in only: calls the real embedding and chat providers, and may incur costs. */
@EnabledIfEnvironmentVariable(named="RUN_GEMINI_MANUAL_TEST",matches="true")
@EnabledIfEnvironmentVariable(named="GOOGLE_API_KEY",matches=".+")
@SpringBootTest(properties={"app.ai.enabled=true","app.ai.provider=gemini"})
@Import(PostgresTestConfiguration.class)
class GeminiManualTests {
    @Autowired AgronomicAiClient client;
    @Autowired AgronomyKnowledge knowledge;
    @Autowired Validator validator;
    @Test void realGeminiReturnsValidStructuredAdviceWithInstitutionalSources() {
        var context=RagAndPromptTests.context();
        var rag=knowledge.retrieve(context);
        assertThat(rag.sources()).isNotEmpty();
        var advice=client.analyze(context,rag);
        assertThat(validator.validate(advice)).isEmpty();
    }
}
