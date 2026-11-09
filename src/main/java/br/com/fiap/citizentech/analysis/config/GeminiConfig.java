package br.com.fiap.citizentech.analysis.config;

import br.com.fiap.citizentech.analysis.ai.*;
import br.com.fiap.citizentech.analysis.rag.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.*;
import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="app.ai.enabled",havingValue="true")
@Import(GeminiConfig.Provider.class)
public class GeminiConfig {
    @ConditionalOnProperty(name="app.ai.provider",havingValue="gemini",matchIfMissing=true)
    @Import({GoogleGenAiChatAutoConfiguration.class, GoogleGenAiEmbeddingConnectionAutoConfiguration.class,
            GoogleGenAiTextEmbeddingAutoConfiguration.class,PgVectorStoreAutoConfiguration.class})
    static class Provider {
        Provider(Environment env) {
            if(env.getProperty("GOOGLE_API_KEY","").isBlank())
                throw new IllegalStateException("GOOGLE_API_KEY é obrigatória quando app.ai.enabled=true");
        }
        @Bean AgronomicAiClient agronomicAiClient(ChatModel model,ObjectMapper mapper,AiProperties properties) {
            return new GeminiAgronomicAiClient(ChatClient.create(model),mapper,properties);
        }
        @Bean AgronomyKnowledge agronomyKnowledge(VectorStore store,AiProperties properties) {
            return new PgVectorAgronomyKnowledge(store,properties);
        }
        @Bean AgronomyRagLoader agronomyRagLoader(VectorStore store,org.springframework.jdbc.core.JdbcTemplate jdbc,
                                                  AiProperties properties) {
            return new AgronomyRagLoader(store,jdbc,properties);
        }
    }
}
