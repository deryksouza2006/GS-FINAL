package br.com.fiap.citizentech.analysis;

import br.com.fiap.citizentech.analysis.ai.*;
import br.com.fiap.citizentech.analysis.dto.*;
import br.com.fiap.citizentech.analysis.domain.*;
import br.com.fiap.citizentech.analysis.rag.AgronomyKnowledge;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@TestConfiguration(proxyBeanMethods=false)
public class FakeAnalysisConfiguration {
    @Bean FakeClient fakeClient() { return new FakeClient(); }
    @Bean AgronomyKnowledge fakeKnowledge() {
        return context -> new RagContext(List.of(new RagContext.Passage("Fonte de teste","Avaliar os dados de campo.")),
                List.of(new RetrievedSource("source-test","Manejo de irrigação","Embrapa","https://www.embrapa.br","1")),false);
    }
    public static class FakeClient implements AgronomicAiClient {
        final AtomicInteger calls=new AtomicInteger();
        volatile AnalysisContext captured;
        volatile String mode="ok";
        @Override public AgronomicAdvice analyze(AnalysisContext context,RagContext rag) {
            captured=context; calls.incrementAndGet();
            if(mode.equals("fail")) throw new IllegalStateException("API key SECRET resposta bruta email@example.com");
            if(mode.equals("timeout")) {
                try { Thread.sleep(30000); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
                throw new IllegalStateException("Provedor indisponível");
            }
            if(mode.equals("invalid")) return new AgronomicAdvice(null,"","",null,List.of(),List.of(),List.of(),null);
            return new AgronomicAdvice(RiskLevel.MODERATE,"Acompanhar umidade","Dados parciais de campo.",
                    "Confirmar calibração e observar a cultura.",List.of("Conferir o sensor"),
                    List.of("Medições recebidas"),List.of(),DataQuality.PARTIAL);
        }
        void reset() { calls.set(0); captured=null; mode="ok"; }
    }
}
