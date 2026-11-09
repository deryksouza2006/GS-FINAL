package br.com.fiap.citizentech.analysis.ai;

import br.com.fiap.citizentech.analysis.config.AiProperties;
import br.com.fiap.citizentech.analysis.dto.AgronomicAdvice;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;

public class GeminiAgronomicAiClient implements AgronomicAiClient {
    public static final String PROMPT_VERSION="agronomic-v1";
    private final ChatClient chat;
    private final ObjectMapper mapper;
    private final AiProperties properties;
    private final String system;
    public GeminiAgronomicAiClient(ChatClient chat,ObjectMapper mapper,AiProperties properties) {
        this.chat=chat; this.mapper=mapper; this.properties=properties;
        try { system=new ClassPathResource("prompts/agronomic-analysis-v1.txt").getContentAsString(StandardCharsets.UTF_8); }
        catch(java.io.IOException e) { throw new IllegalStateException("Prompt indisponível"); }
    }
    @Override
    public AgronomicAdvice analyze(AnalysisContext context,RagContext knowledge) {
        String data=mapper.writeValueAsString(java.util.Map.of("untrustedFieldData",context,"institutionalEvidence",knowledge));
        if(data.length()>properties.maxContextChars()) throw new IllegalArgumentException("Contexto excede o limite");
        var converter=new BeanOutputConverter<>(AgronomicAdvice.class);
        var options=GoogleGenAiChatOptions.builder().model(properties.model())
                .googleSearchRetrieval(false).responseMimeType("application/json")
                .responseSchema(converter.getJsonSchema());
        return chat.prompt().system(system).user(data).options(options).call().entity(converter);
    }
}
