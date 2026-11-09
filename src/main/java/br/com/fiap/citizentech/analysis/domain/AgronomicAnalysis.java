package br.com.fiap.citizentech.analysis.domain;

import br.com.fiap.citizentech.analysis.dto.*;
import br.com.fiap.citizentech.croparea.domain.CropArea;
import br.com.fiap.citizentech.sensorreading.domain.SensorReading;
import br.com.fiap.citizentech.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.*;

@Getter @Entity @Table(name="agronomic_analyses")
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class AgronomicAnalysis extends BaseEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="reading_id", unique=true, nullable=false)
    private SensorReading reading;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="crop_area_id", nullable=false)
    private CropArea cropArea;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private AnalysisStatus status;
    @Enumerated(EnumType.STRING) @Column(length=20) private RiskLevel riskLevel;
    @Column(length=160) private String title;
    @Column(length=2000) private String summary;
    @Column(length=4200) private String recommendation;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") private List<String> recommendedActions;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") private List<String> observedConditions;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") private List<String> warnings;
    @Enumerated(EnumType.STRING) @Column(length=20) private DataQuality dataQuality;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") private List<RetrievedSource> retrievedSources;
    @Column(length=100) private String modelName;
    @Column(length=30) private String promptVersion;
    @Column(nullable=false) private int attemptCount;
    @Column(length=200) private String errorMessage;
    private Instant startedAt;
    private Instant completedAt;
    private UUID processingToken;
    @Column(nullable=false) private int manualRetryCount;
    private Instant lastRetryAt;

    public AgronomicAnalysis(SensorReading reading) {
        this.reading=reading; this.cropArea=reading.getSensor().getCropArea(); status=AnalysisStatus.PENDING;
        recommendedActions=List.of(); observedConditions=List.of(); warnings=List.of(); retrievedSources=List.of();
    }
    public UUID start(String model, String version) {
        status=AnalysisStatus.PROCESSING; startedAt=Instant.now(); completedAt=null; errorMessage=null;
        processingToken=UUID.randomUUID(); modelName=model; promptVersion=version; attemptCount++;
        return processingToken;
    }
    public void complete(AgronomicAdvice advice, List<RetrievedSource> sources, List<String> limitations) {
        riskLevel=advice.riskLevel(); title=advice.title(); summary=advice.summary();
        recommendation=advice.recommendation()+"\n\nApoio à decisão: esta recomendação não substitui a avaliação de um engenheiro agrônomo.";
        recommendedActions=List.copyOf(advice.recommendedActions()); observedConditions=List.copyOf(advice.observedConditions());
        var combined=new LinkedHashSet<>(advice.warnings()); combined.addAll(limitations);
        warnings=List.copyOf(combined);
        dataQuality=limitations.isEmpty() ? advice.dataQuality()
                : (advice.dataQuality()==DataQuality.INSUFFICIENT ? DataQuality.INSUFFICIENT : DataQuality.PARTIAL);
        retrievedSources=List.copyOf(sources); status=AnalysisStatus.COMPLETED; completedAt=Instant.now(); processingToken=null;
    }
    public void fail(boolean terminal, String error) {
        status=terminal ? AnalysisStatus.FAILED : AnalysisStatus.PENDING;
        errorMessage=error; processingToken=null;
        if (terminal) completedAt=Instant.now();
    }
    public void manualRetry() {
        status=AnalysisStatus.PENDING; attemptCount=0; startedAt=null; completedAt=null; processingToken=null;
        errorMessage=null; manualRetryCount++; lastRetryAt=Instant.now();
    }
}
