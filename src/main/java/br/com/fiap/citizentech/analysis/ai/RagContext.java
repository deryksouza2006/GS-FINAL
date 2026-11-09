package br.com.fiap.citizentech.analysis.ai;
import br.com.fiap.citizentech.analysis.dto.RetrievedSource;
import java.util.List;
public record RagContext(List<Passage> passages, List<RetrievedSource> sources, boolean sufficient) {
    public record Passage(String title, String content) { }
}
