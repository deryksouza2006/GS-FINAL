package br.com.fiap.citizentech.analysis.ai;
import br.com.fiap.citizentech.analysis.dto.AgronomicAdvice;
public interface AgronomicAiClient {
    AgronomicAdvice analyze(AnalysisContext context, RagContext knowledge);
}
