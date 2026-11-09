package br.com.fiap.citizentech.analysis.service;

import br.com.fiap.citizentech.analysis.ai.AnalysisContext;
import br.com.fiap.citizentech.analysis.config.AiProperties;
import br.com.fiap.citizentech.sensorreading.repository.SensorReadingRepository;
import br.com.fiap.citizentech.sensor.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class AnalysisContextService {
    private final SensorReadingRepository readings;
    private final SensorRepository sensors;
    private final AiProperties properties;

    @Transactional(readOnly=true)
    public AnalysisContext assemble(long readingId) {
        var r=readings.findWithContextById(readingId).orElseThrow(() -> new IllegalStateException("Leitura indisponível"));
        var crop=r.getSensor().getCropArea(); var farm=crop.getFarm();
        var now=Instant.now(); var cutoff=now.minus(properties.staleAfter());
        var latest=readings.latestActiveByType(crop.getId()).stream()
                .map(v -> AnalysisContext.Measurement.from(v,cutoff)).toList();
        var types=sensors.activeTypes(crop.getId()).stream().map(Enum::name).toList();
        var limitations=new ArrayList<String>();
        for (var type: List.of("TEMPERATURE","HUMIDITY","SOIL_MOISTURE")) {
            boolean present=latest.stream().anyMatch(m -> switch(type) {
                case "TEMPERATURE" -> m.temperatureCelsius()!=null;
                case "HUMIDITY" -> m.airHumidityPercent()!=null;
                default -> m.soilMoisturePercent()!=null;
            });
            if (!present) limitations.add("Medição ausente: "+type);
        }
        if (r.getMeasuredAt().isBefore(cutoff) || latest.stream().anyMatch(AnalysisContext.Measurement::stale))
            limitations.add("Existem medições desatualizadas; confirme as condições atuais em campo.");
        limitations.add("Estádio da cultura, textura do solo e chuva local não foram informados.");
        return new AnalysisContext(safe(crop.getCropType()),crop.getAreaSize(),crop.getAreaUnit().name(),
                safe(crop.getDescription()),safe(farm.getCity()),farm.getState(),now,
                AnalysisContext.Measurement.from(r,cutoff),latest,types,List.copyOf(limitations));
    }

    // Free text is untrusted data. Redact common contact/credential patterns before sending to a provider.
    private String safe(String value) {
        if(value==null) return null;
        return value.replaceAll("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", "[contato removido]")
                .replaceAll("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+","[token removido]")
                .replaceAll("\\b(?:\\d[ .()+-]?){10,14}\\b","[contato removido]");
    }
}
