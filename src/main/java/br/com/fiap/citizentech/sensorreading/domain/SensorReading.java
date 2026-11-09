package br.com.fiap.citizentech.sensorreading.domain;

import br.com.fiap.citizentech.sensor.domain.Sensor;
import br.com.fiap.citizentech.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Entity
@Table(name = "sensor_readings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SensorReading extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sensor_id", nullable = false)
    private Sensor sensor;
    @Column(name = "temperature_celsius", precision = 6, scale = 3)
    private BigDecimal temperatureCelsius;
    @Column(name = "air_humidity_percent", precision = 6, scale = 3)
    private BigDecimal airHumidityPercent;
    @Column(name = "soil_moisture_percent", precision = 6, scale = 3)
    private BigDecimal soilMoisturePercent;
    @Column(name = "battery_percent", precision = 6, scale = 3)
    private BigDecimal batteryPercent;
    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private ReadingSource source;
    @Enumerated(EnumType.STRING) @Column(name = "analysis_status", nullable = false, length = 30)
    private ReadingAnalysisStatus analysisStatus;

    public SensorReading(Sensor sensor, BigDecimal temperatureCelsius, BigDecimal airHumidityPercent,
                         BigDecimal soilMoisturePercent, BigDecimal batteryPercent, Instant measuredAt) {
        this.sensor = sensor;
        this.temperatureCelsius = temperatureCelsius;
        this.airHumidityPercent = airHumidityPercent;
        this.soilMoisturePercent = soilMoisturePercent;
        this.batteryPercent = batteryPercent;
        this.measuredAt = measuredAt == null ? Instant.now() : measuredAt;
        this.source = ReadingSource.MANUAL;
        this.analysisStatus = ReadingAnalysisStatus.PENDING_PUBLICATION;
    }

    public void analyzed() { analysisStatus = ReadingAnalysisStatus.ANALYZED; }
    public void analysisFailed() { analysisStatus = ReadingAnalysisStatus.FAILED; }
    public void retryAnalysis() { analysisStatus = ReadingAnalysisStatus.QUEUED_FOR_ANALYSIS; }

    public void queueForAnalysis() {
        if (analysisStatus == ReadingAnalysisStatus.PENDING_PUBLICATION) {
            analysisStatus = ReadingAnalysisStatus.QUEUED_FOR_ANALYSIS;
        }
    }
}
