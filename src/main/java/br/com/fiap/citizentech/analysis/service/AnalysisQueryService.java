package br.com.fiap.citizentech.analysis.service;

import br.com.fiap.citizentech.analysis.config.AiProperties;
import br.com.fiap.citizentech.analysis.domain.*;
import br.com.fiap.citizentech.analysis.dto.*;
import br.com.fiap.citizentech.analysis.repository.AgronomicAnalysisRepository;
import br.com.fiap.citizentech.croparea.repository.CropAreaRepository;
import br.com.fiap.citizentech.sensorreading.domain.ReadingAnalysisStatus;
import br.com.fiap.citizentech.sensorreading.event.SensorReadingCreated;
import br.com.fiap.citizentech.sensorreading.repository.SensorReadingRepository;
import br.com.fiap.citizentech.shared.exception.BusinessException;
import br.com.fiap.citizentech.shared.outbox.*;
import br.com.fiap.citizentech.user.domain.*;
import br.com.fiap.citizentech.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;

@Service @RequiredArgsConstructor @Transactional(readOnly=true) @PreAuthorize("hasRole('PRODUCER')")
public class AnalysisQueryService {
    private final AgronomicAnalysisRepository analyses;
    private final SensorReadingRepository readings;
    private final CropAreaRepository crops;
    private final UserRepository users;
    private final OutboxRepository outbox;
    private final ObjectMapper mapper;
    private final AiProperties properties;

    public AnalysisPageResponse list(String email,int page,int size) {
        return AnalysisPageResponse.from(analyses.findAllByCropAreaFarmOwnerId(producer(email).getId(),pageable(page,size))
                .map(AnalysisResponse::from));
    }
    public AnalysisPageResponse byCrop(String email,long cropId,int page,int size) {
        long owner=producer(email).getId();
        crops.findByIdAndFarmOwnerId(cropId,owner).orElseThrow(this::notFound);
        return AnalysisPageResponse.from(analyses.findAllByCropAreaIdAndCropAreaFarmOwnerId(cropId,owner,pageable(page,size))
                .map(AnalysisResponse::from));
    }
    public AnalysisResponse get(String email,long id) {
        return analyses.findByIdAndCropAreaFarmOwnerId(id,producer(email).getId())
                .map(AnalysisResponse::from).orElseThrow(this::notFound);
    }
    public AnalysisResponse byReading(String email,long readingId) {
        return analyses.findByReadingIdAndCropAreaFarmOwnerId(readingId,producer(email).getId())
                .map(AnalysisResponse::from).orElseThrow(this::notFound);
    }
    @Transactional
    public AnalysisResponse retry(String email,long readingId) {
        var reading=readings.findOwnedForUpdate(readingId,producer(email).getId()).orElseThrow(this::notFound);
        var a=analyses.findByReadingId(readingId).orElseGet(() -> new AgronomicAnalysis(reading));
        boolean stale=a.getStatus()==AnalysisStatus.PROCESSING &&
                a.getStartedAt().plus(properties.processingTimeout()).isBefore(Instant.now());
        if(a.getStatus()==AnalysisStatus.COMPLETED || reading.getAnalysisStatus()==ReadingAnalysisStatus.ANALYZED
                || (a.getStatus()==AnalysisStatus.PROCESSING && !stale)
                || (a.getStatus()!=AnalysisStatus.FAILED && !stale
                    && reading.getAnalysisStatus()!=ReadingAnalysisStatus.QUEUED_FOR_ANALYSIS))
            throw new BusinessException(HttpStatus.CONFLICT,"Estado da leitura não permite nova análise");
        if(a.getManualRetryCount()>=properties.maxManualRetries())
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,"Limite de novas análises atingido");
        if(a.getLastRetryAt()!=null && a.getLastRetryAt().plus(properties.retryCooldown()).isAfter(Instant.now()))
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,"Aguarde antes de solicitar outra análise");
        a.manualRetry(); reading.retryAnalysis(); analyses.saveAndFlush(a);
        UUID id=UUID.randomUUID();
        outbox.saveAndFlush(new OutboxEvent(id,"SensorReading",readingId,"SensorReadingCreated",
                mapper.writeValueAsString(SensorReadingCreated.from(id,reading))));
        return AnalysisResponse.from(a);
    }
    private User producer(String email) {
        var user=users.findByEmailIgnoreCase(email).orElseThrow(this::notFound);
        if(!user.isEnabled() || user.getRole()!=UserRole.PRODUCER)
            throw new BusinessException(HttpStatus.FORBIDDEN,"Acesso permitido somente a produtores ativos");
        return user;
    }
    private Pageable pageable(int page,int size) {
        return PageRequest.of(page,size,Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("id")));
    }
    private BusinessException notFound() { return new BusinessException(HttpStatus.NOT_FOUND,"Análise ou recurso não encontrado"); }
}
