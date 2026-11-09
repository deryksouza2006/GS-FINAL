package br.com.fiap.citizentech.analysis.service;

import br.com.fiap.citizentech.analysis.ai.*;
import br.com.fiap.citizentech.analysis.config.AiProperties;
import br.com.fiap.citizentech.analysis.dto.AgronomicAdvice;
import br.com.fiap.citizentech.analysis.rag.AgronomyKnowledge;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Validator;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.*;

@Service
public class AgronomicAnalysisService {
    private final AiProperties properties;
    private final AnalysisTransactions transactions;
    private final AnalysisContextService contexts;
    private final ObjectProvider<AgronomicAiClient> client;
    private final ObjectProvider<AgronomyKnowledge> knowledge;
    private final Validator validator;
    private final ExecutorService executor=new ThreadPoolExecutor(4,4,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4),
            Thread.ofPlatform().daemon().name("agronomic-ai-",0).factory(),new ThreadPoolExecutor.AbortPolicy());

    public AgronomicAnalysisService(AiProperties properties,AnalysisTransactions transactions,AnalysisContextService contexts,
            ObjectProvider<AgronomicAiClient> client,ObjectProvider<AgronomyKnowledge> knowledge,Validator validator) {
        this.properties=properties; this.transactions=transactions; this.contexts=contexts;
        this.client=client; this.knowledge=knowledge; this.validator=validator;
    }
    public void process(long readingId) {
        if(!properties.enabled()) return;
        var token=transactions.begin(readingId);
        if(token==null) return;
        Future<Result> pending=null;
        try {
            pending=executor.submit(() -> {
                var context=contexts.assemble(readingId);
                var rag=knowledge.getObject().retrieve(context);
                var advice=client.getObject().analyze(context,rag);
                if(advice==null || !validator.validate(advice).isEmpty())
                    throw new IllegalArgumentException("Saída estruturada inválida");
                var limitations=new ArrayList<>(context.limitations());
                if(!rag.sufficient()) limitations.add("Contexto RAG insuficiente; confirme as orientações com um profissional.");
                return new Result(advice,rag,List.copyOf(limitations));
            });
            var result=pending.get(properties.callTimeout().toMillis(),TimeUnit.MILLISECONDS);
            transactions.complete(readingId,token,result.advice(),result.rag().sources(),result.limitations());
        } catch(Exception failure) {
            if(pending!=null) pending.cancel(true);
            if(failure instanceof InterruptedException) Thread.currentThread().interrupt();
            boolean terminal=transactions.fail(readingId,token,failure instanceof TimeoutException);
            // Never propagate provider messages, response bodies, prompts or credentials to AMQP logs.
            if(terminal) throw new AmqpRejectAndDontRequeueException("Análise falhou após tentativas controladas");
            throw new IllegalStateException("Análise indisponível; nova tentativa será realizada");
        }
    }
    private record Result(AgronomicAdvice advice,RagContext rag,List<String> limitations) { }
    @PreDestroy void stop() { executor.shutdownNow(); }
}
