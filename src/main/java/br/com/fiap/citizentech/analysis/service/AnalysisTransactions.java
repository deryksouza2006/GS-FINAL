package br.com.fiap.citizentech.analysis.service;

import br.com.fiap.citizentech.analysis.ai.GeminiAgronomicAiClient;
import br.com.fiap.citizentech.analysis.config.AiProperties;
import br.com.fiap.citizentech.analysis.domain.*;
import br.com.fiap.citizentech.analysis.dto.*;
import br.com.fiap.citizentech.analysis.repository.AgronomicAnalysisRepository;
import br.com.fiap.citizentech.sensorreading.repository.SensorReadingRepository;
import br.com.fiap.citizentech.user.domain.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class AnalysisTransactions {
    private final SensorReadingRepository readings;
    private final AgronomicAnalysisRepository analyses;
    private final AiProperties properties;
    private final br.com.fiap.citizentech.shared.outbox.OutboxRepository outbox;
    private final tools.jackson.databind.ObjectMapper mapper;

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public int recoverAbandoned() {
        int recovered=0;
        var cutoff=Instant.now().minus(properties.processingTimeout());
        for(Long id:analyses.findAbandonedReadingIds(cutoff,org.springframework.data.domain.PageRequest.of(0,10))) {
            var reading=readings.findForUpdate(id);
            if(reading.isEmpty()) continue;
            var a=analyses.findByReadingId(id).orElse(null);
            if(a==null || a.getStatus()!=AnalysisStatus.PROCESSING || !a.getStartedAt().isBefore(cutoff)) continue;
            boolean terminal=a.getAttemptCount()>=properties.maxAttempts();
            a.fail(terminal,"Processamento interrompido; evento reenfileirado");
            if(terminal) reading.get().analysisFailed(); else reading.get().retryAnalysis();
            UUID eventId=UUID.randomUUID();
            outbox.save(new br.com.fiap.citizentech.shared.outbox.OutboxEvent(eventId,"SensorReading",id,"SensorReadingCreated",
                    mapper.writeValueAsString(br.com.fiap.citizentech.sensorreading.event.SensorReadingCreated.from(eventId,reading.get()))));
            recovered++;
        }
        outbox.flush(); return recovered;
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public UUID begin(long readingId) {
        var reading=readings.findForUpdate(readingId).orElseThrow(() -> new IllegalStateException("Leitura indisponível"));
        if(reading.getAnalysisStatus()==br.com.fiap.citizentech.sensorreading.domain.ReadingAnalysisStatus.ANALYZED) return null;
        var owner=reading.getSensor().getCropArea().getFarm().getOwner();
        if(!owner.isEnabled() || owner.getRole()!=UserRole.PRODUCER)
            throw new AmqpRejectAndDontRequeueException("Produtor indisponível para análise");
        var a=analyses.findByReadingId(readingId).orElseGet(() -> analyses.save(new AgronomicAnalysis(reading)));
        if(a.getStatus()==AnalysisStatus.COMPLETED) return null;
        if(a.getStatus()==AnalysisStatus.FAILED)
            throw new AmqpRejectAndDontRequeueException("Análise esgotou as tentativas; solicite retry pela API");
        if(a.getStatus()==AnalysisStatus.PROCESSING
                && a.getStartedAt().plus(properties.processingTimeout()).isAfter(Instant.now())) return null;
        UUID token=a.start(properties.model(),GeminiAgronomicAiClient.PROMPT_VERSION);
        analyses.saveAndFlush(a);
        return token;
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void complete(long readingId,UUID token,AgronomicAdvice advice,List<RetrievedSource> sources,List<String> limitations) {
        var reading=readings.findForUpdate(readingId);
        if(reading.isEmpty()) return; // Explicit deletion while the external request was in flight.
        var a=analyses.findByReadingId(readingId).orElse(null);
        if(a==null || !token.equals(a.getProcessingToken())) return; // Expired worker cannot overwrite a newer attempt.
        a.complete(advice,sources,limitations); reading.get().analyzed(); analyses.flush();
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public boolean fail(long readingId,UUID token,boolean timeout) {
        var reading=readings.findForUpdate(readingId);
        if(reading.isEmpty()) return true;
        var a=analyses.findByReadingId(readingId).orElse(null);
        if(a==null || !token.equals(a.getProcessingToken())) return true;
        boolean terminal=a.getAttemptCount()>=properties.maxAttempts();
        a.fail(terminal,timeout ? "Tempo limite ao consultar o provedor de análise" : "Não foi possível validar ou concluir a análise");
        if(terminal) reading.get().analysisFailed();
        analyses.flush(); return terminal;
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void exhausted(long readingId) {
        var reading=readings.findForUpdate(readingId);
        if(reading.isEmpty()) return;
        analyses.findByReadingId(readingId).ifPresent(a -> {
            if(a.getStatus()!=AnalysisStatus.COMPLETED && a.getStatus()!=AnalysisStatus.PROCESSING) {
                a.fail(true,"Tentativas de entrega esgotadas"); reading.get().analysisFailed();
            }
        });
    }
}
