package br.com.fiap.citizentech.analysis.rag;

import br.com.fiap.citizentech.analysis.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Slf4j
public class AgronomyRagLoader implements ApplicationRunner {
    private final VectorStore store;
    private final JdbcTemplate jdbc;
    private final AiProperties properties;
    public AgronomyRagLoader(VectorStore store,JdbcTemplate jdbc,AiProperties properties) {
        this.store=store; this.jdbc=jdbc; this.properties=properties;
    }
    @Override public void run(ApplicationArguments args) {
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor(
                Thread.ofPlatform().daemon().name("agronomy-ingestion").factory());
        try {
            var work=executor.submit(this::load);
            try { work.get(properties.callTimeout().toMillis(),java.util.concurrent.TimeUnit.MILLISECONDS); }
            catch(Exception failure) {
                work.cancel(true);
                if(failure instanceof InterruptedException) Thread.currentThread().interrupt();
                log.warn("RAG indisponível na inicialização; análises indicarão limitação ou falha do provedor");
            }
        } finally { executor.shutdownNow(); }
    }
    public int load() throws Exception {
        int loaded=0;
        for(var resource:new PathMatchingResourcePatternResolver().getResources("classpath*:rag/agronomy/*.md")) {
            String text=resource.getContentAsString(StandardCharsets.UTF_8).replace("\r\n","\n");
            String[] parts=text.split("\n---\n",2);
            if(parts.length!=2) throw new IllegalStateException("Metadados RAG ausentes");
            Map<String,Object> metadata=new LinkedHashMap<>();
            for(String line:parts[0].lines().toList()) {
                int colon=line.indexOf(':');
                if(colon>0) metadata.put(line.substring(0,colon).trim(),line.substring(colon+1).trim());
            }
            for(String key:List.of("sourceId","title","institution","url","topic","version","accessedAt"))
                if(!metadata.containsKey(key)) throw new IllegalStateException("Documento RAG incompleto");
            metadata.put("corpus","agronomy");
            String key=metadata.get("sourceId").toString();
            String checksum=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((text+properties.embeddingModel()+":768").getBytes(StandardCharsets.UTF_8)));
            var existing=jdbc.queryForList("SELECT checksum FROM rag_ingestion WHERE corpus_key=?",String.class,key);
            if(existing.contains(checksum)) continue;
            var chunks=new ArrayList<Document>();
            String content=parts[1].trim();
            // The corpus is deliberately short. Bound each chunk without splitting UTF-16 surrogate pairs.
            for(int offset=0,index=0;offset<content.length();index++) {
                int end=Math.min(offset+1000,content.length());
                if(end<content.length() && Character.isHighSurrogate(content.charAt(end-1))) end--;
                String id=UUID.nameUUIDFromBytes((key+":"+metadata.get("version")+":"+index).getBytes(StandardCharsets.UTF_8)).toString();
                chunks.add(Document.builder().id(id).text(content.substring(offset,end)).metadata(metadata).build());
                offset=end;
            }
            store.add(chunks); // deterministic IDs are upserted by PGVector
            var arguments=new ArrayList<Object>(); arguments.add(key);
            chunks.forEach(chunk -> arguments.add(UUID.fromString(chunk.getId())));
            String placeholders=String.join(",",Collections.nCopies(chunks.size(),"?"));
            jdbc.update("DELETE FROM agronomy_vector_store WHERE metadata->>'sourceId'=? AND id NOT IN ("+
                    placeholders+")",arguments.toArray());
            jdbc.update("INSERT INTO rag_ingestion(corpus_key,checksum,updated_at) VALUES (?,?,CURRENT_TIMESTAMP) "+
                    "ON CONFLICT(corpus_key) DO UPDATE SET checksum=EXCLUDED.checksum,updated_at=EXCLUDED.updated_at",key,checksum);
            loaded+=chunks.size();
        }
        log.info("RAG: {} trechos carregados/atualizados",loaded);
        return loaded;
    }
}
