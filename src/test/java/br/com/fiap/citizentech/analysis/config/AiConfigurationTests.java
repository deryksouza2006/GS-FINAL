package br.com.fiap.citizentech.analysis.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

class AiConfigurationTests {
    @Configuration(proxyBeanMethods=false)
    @EnableConfigurationProperties(AiProperties.class)
    static class PropertiesConfig { }
    ApplicationContextRunner configured() throws Exception {
        var yaml=new YamlPropertySourceLoader().load("application",new ClassPathResource("application.yml")).getFirst();
        return new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class,GeminiConfig.class)
                .withInitializer(ctx->ctx.getEnvironment().getPropertySources().addLast(yaml));
    }
    @Test void disabledAiStartsWithoutKeyAndBindsDefinitiveDefaultsToGoogleGenai() throws Exception {
        configured().withPropertyValues("APP_AI_ENABLED=false","GOOGLE_API_KEY=").run(ctx->{
            assertThat(ctx).hasNotFailed();
            var p=ctx.getBean(AiProperties.class);
            assertThat(p.model()).isEqualTo("gemini-3.5-flash-lite");
            assertThat(p.embeddingModel()).isEqualTo("gemini-embedding-001");
            assertThat(p.callTimeout()).isEqualTo(Duration.ofSeconds(90));
            assertThat(p.processingTimeout()).isEqualTo(Duration.ofMinutes(5));
            var binder=Binder.get(ctx.getEnvironment());
            var chat=binder.bind("spring.ai.google.genai.chat",Bindable.of(org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatProperties.class)).get();
            assertThat(chat.getModel()).isEqualTo(p.model());assertThat(chat.getTemperature()).isEqualTo(0.2);
            assertThat(chat.getResponseMimeType()).isEqualTo("application/json");
            assertThat(chat.getThinkingBudget()).isNull();
            var embedding=binder.bind("spring.ai.google.genai.embedding.text",Bindable.of(org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiTextEmbeddingProperties.class)).get();
            assertThat(embedding.getModel()).isEqualTo("gemini-embedding-001");assertThat(embedding.getDimensions()).isEqualTo(768);
            assertThat(ctx).doesNotHaveBean(org.springframework.ai.chat.model.ChatModel.class);
            assertThat(ctx).doesNotHaveBean(org.springframework.ai.vectorstore.VectorStore.class);
            var retryProperties=binder.bind("spring.ai.retry",Bindable.of(org.springframework.ai.retry.autoconfigure.SpringAiRetryProperties.class)).get();
            var retry=new org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration().retryTemplate(retryProperties);
            var attempts=new java.util.concurrent.atomic.AtomicInteger();
            assertThatThrownBy(()->retry.execute(()->{attempts.incrementAndGet();throw new org.springframework.ai.retry.TransientAiException("test failure");}));
            assertThat(attempts).hasValue(1);
        });
    }
    @Test void modelAndTimeoutRemainOverridableWithoutEditingConfiguration() throws Exception {
        configured().withPropertyValues("APP_AI_ENABLED=false","AI_CHAT_MODEL=local-model-test","AI_CALL_TIMEOUT=PT20S","AI_PROCESSING_TIMEOUT=PT1M")
                .run(ctx->{assertThat(ctx).hasNotFailed();assertThat(ctx.getBean(AiProperties.class).model()).isEqualTo("local-model-test");
                    assertThat(ctx.getBean(AiProperties.class).callTimeout()).isEqualTo(Duration.ofSeconds(20));});
    }
    @Test void fakeProviderNeverInstantiatesGoogleClientsOrRequiresAKey() throws Exception {
        configured().withPropertyValues("app.ai.enabled=true","APP_AI_ENABLED=true","app.ai.provider=fake","GOOGLE_API_KEY=").run(ctx->{
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).doesNotHaveBean(com.google.genai.Client.class);
            assertThat(ctx).doesNotHaveBean(org.springframework.ai.chat.model.ChatModel.class);
        });
    }
    @Test void invalidTimeoutOrderingOrMobileDeadlineIsRejected() throws Exception {
        for(String[] timing:new String[][]{{"PT90S","PT90S"},{"PT180S","PT5M"},{"PT0S","PT5M"}})
            configured().withPropertyValues("APP_AI_ENABLED=false","AI_CALL_TIMEOUT="+timing[0],"AI_PROCESSING_TIMEOUT="+timing[1])
                    .run(ctx->assertThat(ctx).hasFailed());
    }
    @Test void enabledProviderRequiresAKeyAndCannotChangeExistingVectorDimensions() {
        assertThatThrownBy(()->new GeminiConfig.Provider(new MockEnvironment())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GOOGLE_API_KEY");
        assertThatThrownBy(()->new GeminiConfig.Provider(new MockEnvironment()
                .withProperty("spring.ai.vectorstore.pgvector.dimensions","3072"))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("768");
    }
    @Test void googleSdkHasOneAttemptAndEmbeddingUsesTheSameBoundedClient() throws Exception {
        configured().withPropertyValues("APP_AI_ENABLED=false").run(ctx->{
            var env=new MockEnvironment().withProperty("GOOGLE_API_KEY","local-unit-test-placeholder");
            var provider=new GeminiConfig.Clients();
            try(var client=provider.googleGenAiClient(env,ctx.getBean(AiProperties.class))) {
                Object api=org.springframework.test.util.ReflectionTestUtils.getField(client,"apiClient");
                var options=(com.google.genai.types.HttpOptions)org.springframework.test.util.ReflectionTestUtils.getField(api,"httpOptions");
                assertThat(options.retryOptions().orElseThrow().attempts().orElseThrow()).isEqualTo(1);
                assertThat(options.timeout().orElseThrow()).isEqualTo(90000);
                assertThat(provider.googleGenAiEmbeddingConnectionDetails(client).getGenAiClient()).isSameAs(client);
            }
        });
    }
    @Test void enabledSpringAiCompositionBuildsChatAndEmbeddingWithoutCallingGoogle() throws Exception {
        configured().withPropertyValues("app.ai.enabled=true","APP_AI_ENABLED=true","GOOGLE_API_KEY=local-unit-test-placeholder")
                .withBean(tools.jackson.databind.ObjectMapper.class,()->tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build())
                .withBean(org.springframework.ai.model.tool.ToolCallingManager.class,()->org.mockito.Mockito.mock(org.springframework.ai.model.tool.ToolCallingManager.class))
                .withBean(org.springframework.jdbc.core.JdbcTemplate.class,()->org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class))
                .withBean("testVectorStore",org.springframework.ai.vectorstore.VectorStore.class,()->org.mockito.Mockito.mock(org.springframework.ai.vectorstore.VectorStore.class),definition->definition.setPrimary(true))
                .run(ctx->{
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(org.springframework.ai.chat.model.ChatModel.class);
                    assertThat(ctx).hasSingleBean(org.springframework.ai.embedding.EmbeddingModel.class);
                    assertThat(ctx).hasSingleBean(com.google.genai.Client.class);
                    assertThat(ctx.getBean(org.springframework.ai.chat.model.ChatModel.class).getOptions().getModel()).isEqualTo("gemini-3.5-flash-lite");
                    assertThat(ctx.getBean(org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails.class).getGenAiClient())
                            .isSameAs(ctx.getBean(com.google.genai.Client.class));
                });
    }
}
