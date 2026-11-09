package br.com.fiap.citizentech.analysis.rag;

import br.com.fiap.citizentech.analysis.ai.*;
import br.com.fiap.citizentech.analysis.config.AiProperties;
import br.com.fiap.citizentech.analysis.dto.RetrievedSource;
import org.springframework.ai.vectorstore.*;
import java.util.*;

public class PgVectorAgronomyKnowledge implements AgronomyKnowledge {
    private final VectorStore store;
    private final AiProperties properties;
    public PgVectorAgronomyKnowledge(VectorStore store,AiProperties properties) { this.store=store; this.properties=properties; }

    @Override
    public RagContext retrieve(AnalysisContext context) {
        // Do not use descriptions as instructions or identifiers in the semantic query.
        String query="Cultura: "+context.cropType()+". Manejo de irrigação, sensores, umidade do solo, estresse hídrico. "+
                "Medições: "+context.receivedReading()+". Dados ausentes: "+context.limitations();
        var docs=store.similaritySearch(SearchRequest.builder().query(query).topK(properties.topK())
                .similarityThreshold(properties.similarityThreshold()).filterExpression("corpus == 'agronomy'").build());
        if(docs==null) docs=List.of();
        var sources=new LinkedHashSet<RetrievedSource>();
        var passages=new ArrayList<RagContext.Passage>();
        for(var doc:docs) {
            var m=doc.getMetadata();
            if(!m.keySet().containsAll(List.of("sourceId","title","institution","url","version"))) continue;
            sources.add(new RetrievedSource(m.get("sourceId").toString(),m.get("title").toString(),
                    m.get("institution").toString(),m.get("url").toString(),m.get("version").toString()));
            String content=doc.getText();
            passages.add(new RagContext.Passage(m.get("title").toString(),content.substring(0,Math.min(content.length(),1600))));
        }
        return new RagContext(List.copyOf(passages),List.copyOf(sources),passages.size()>=3);
    }
}
