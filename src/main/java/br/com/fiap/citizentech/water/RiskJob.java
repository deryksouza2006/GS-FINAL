package br.com.fiap.citizentech.water;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.List;

@Entity @Table(name="risk_jobs") @Getter @NoArgsConstructor
public class RiskJob {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long cropAreaId;
    @Column(nullable=false) private Long ownerId;
    private Long profileId;
    private Double fieldCapacity;
    private Double wiltingPoint;
    @Column(nullable=false) private boolean demo;
    @Column(nullable=false,length=40) private String status;
    @Column(nullable=false) private Instant createdAt;
    private Instant completedAt;
    private Instant startedAt;
    private Instant latestRainAt;
    @Column(nullable=false) private int attempts;
    @Column(length=200) private String errorMessage;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb")
    private WaterRiskCalculator.Thresholds thresholds;

    public void captureThresholds(WaterRiskCalculator.Thresholds value) {
        thresholds = value;
        rulesVersion = value.version();
    }

    public boolean terminal() { return result != null || "FAILED".equals(status); }

    public void start() {
        status = "PROCESSING";
        startedAt = Instant.now();
        attempts++;
    }
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb") private List<WaterRiskCalculator.Measurement> measurements;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") private WaterRiskCalculator.Result result;
    @Column(nullable=false,length=30) private String rulesVersion;
    @Column(length=30) private String alertStatus;
    private Instant acknowledgedAt;
    private Instant resolvedAt;
    private Long acknowledgedBy;
    private Long resolvedBy;
    @Column(nullable=false) private int recoveryCount;

    public record Alert(String type, String title, String justification, String level, List<String> rules,
                        boolean demo, String status, Instant createdAt, Instant acknowledgedAt, Long acknowledgedBy,
                        Instant resolvedAt, Long resolvedBy) { }

    public Alert getAlert() {
        if (alertStatus == null || result == null) return null;
        String rule = result.rulesTriggered().stream().findFirst().orElse("WATER_RISK");
        String type = switch (rule) {
            case "LOW_WATER_AVAILABILITY" -> "LOW_SOIL_WATER";
            case "STALE_SENSOR_DATA" -> "SENSOR_DATA_STALE";
            default -> rule;
        };
        String title = switch (type) {
            case "LOW_SOIL_WATER" -> "Disponibilidade hídrica baixa";
            case "HIGH_ATMOSPHERIC_DEMAND" -> "Demanda atmosférica alta";
            case "RAPID_SOIL_DRYING" -> "Secagem rápida do solo";
            case "EXCESS_SOIL_WATER" -> "Umidade acima da capacidade de campo";
            default -> "Verifique os dados do cultivo";
        };
        return new Alert(type, title, title + ". Regras acionadas: " + String.join(", ", result.rulesTriggered()),
                result.riskLevel().name(), result.rulesTriggered(), result.demo(), alertStatus, completedAt,
                acknowledgedAt, acknowledgedBy, resolvedAt, resolvedBy);
    }

    public void requeue() {
        recoveryCount++;
        status = "PENDING";
    }
    @Column(length=80) private String cropType;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") private java.util.Map<String,RainfallCalculator.Total> rainfall;
    public void captureRainfall(java.util.Map<String,RainfallCalculator.Total> totals, Instant latestRainAt) {
        rainfall=totals;
        this.latestRainAt=latestRainAt;
    }
    public Long getSecondsSinceRain() {
        return latestRainAt == null ? null : java.time.Duration.between(latestRainAt, createdAt).toSeconds();
    }
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") private java.util.Map<String,Object> guidance;
    public void captureCropType(String type) { cropType=type; }
    public void guidance(java.util.Map<String,Object> value) { guidance=value; status="COMPLETED"; }
    public void fail() {
        if (!terminal()) {
            status = "FAILED";
            errorMessage = "Não foi possível processar a análise após as tentativas previstas";
            completedAt = Instant.now();
        }
    }
    public RiskJob(Long area,Long owner,AgronomicProfile profile,List<WaterRiskCalculator.Measurement> measurements,Instant now) {
        cropAreaId=area; ownerId=owner; createdAt=now; status="PENDING"; rulesVersion="demo-v1";
        this.measurements=List.copyOf(measurements);
        if(profile!=null) { profileId=profile.getId(); fieldCapacity=profile.getFieldCapacityPercent();
            wiltingPoint=profile.getWiltingPointPercent(); demo=profile.isDemo(); }
        // MVP thresholds are demonstration rules, even when the readings are real.
        demo=true;
    }
    public void complete(WaterRiskCalculator.Result result) {
        this.result=result; completedAt=Instant.now(); status="COMPLETED_WITHOUT_AI";
        if(result.riskLevel()!=WaterRiskCalculator.Level.NORMAL) alertStatus="OPEN";
    }
    public void acknowledge() {
        if ("OPEN".equals(alertStatus)) {
            alertStatus = "ACKNOWLEDGED";
            acknowledgedAt = Instant.now();
            acknowledgedBy = ownerId;
        }
    }
    public void resolve() {
        if (alertStatus != null && !"RESOLVED".equals(alertStatus)) {
            alertStatus = "RESOLVED";
            resolvedAt = Instant.now();
            resolvedBy = ownerId;
        }
    }
}
