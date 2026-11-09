package br.com.fiap.citizentech.analysis.dto;

import br.com.fiap.citizentech.analysis.domain.*;
import java.time.Instant;
import java.util.List;

public record AnalysisResponse(Long id,Long readingId,Long cropAreaId,String cropAreaName,String cropType,
        AnalysisStatus status,RiskLevel riskLevel,String title,String summary,String recommendation,
        List<String> recommendedActions,List<String> observedConditions,List<String> warnings,
        DataQuality dataQuality,List<RetrievedSource> retrievedSources,String modelName,String promptVersion,
        Instant startedAt,Instant completedAt,Instant createdAt) {
    public static AnalysisResponse from(AgronomicAnalysis a) {
        var c=a.getCropArea();
        return new AnalysisResponse(a.getId(),a.getReading().getId(),c.getId(),c.getName(),c.getCropType(),
                a.getStatus(),a.getRiskLevel(),a.getTitle(),a.getSummary(),a.getRecommendation(),
                a.getRecommendedActions(),a.getObservedConditions(),a.getWarnings(),a.getDataQuality(),
                a.getRetrievedSources(),a.getModelName(),a.getPromptVersion(),a.getStartedAt(),a.getCompletedAt(),a.getCreatedAt());
    }
}
