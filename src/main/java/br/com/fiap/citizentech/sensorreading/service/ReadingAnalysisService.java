package br.com.fiap.citizentech.sensorreading.service;

import br.com.fiap.citizentech.sensorreading.domain.ReadingAnalysisStatus;
import br.com.fiap.citizentech.sensorreading.event.SensorReadingCreated;
import br.com.fiap.citizentech.sensorreading.repository.SensorReadingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ReadingAnalysisService {
    private final SensorReadingRepository readings;

    @Transactional
    public void queue(SensorReadingCreated event) {
        if (event.eventId() == null || event.readingId() == null || event.sensorId() == null) {
            throw new IllegalArgumentException("Evento de leitura inválido");
        }
        var reading = readings.findForUpdate(event.readingId())
                .orElseThrow(() -> new IllegalStateException("Leitura do evento não encontrada"));
        if (!Objects.equals(reading.getSensor().getId(), event.sensorId())) {
            throw new IllegalArgumentException("Sensor do evento não corresponde à leitura");
        }
        if (reading.getAnalysisStatus() == ReadingAnalysisStatus.PENDING_PUBLICATION) {
            reading.queueForAnalysis();
            readings.saveAndFlush(reading);
        } else if (reading.getAnalysisStatus() != ReadingAnalysisStatus.QUEUED_FOR_ANALYSIS
                && reading.getAnalysisStatus() != ReadingAnalysisStatus.ANALYZED) {
            throw new IllegalStateException("Estado da leitura não permite preparação para análise");
        }
    }
}
