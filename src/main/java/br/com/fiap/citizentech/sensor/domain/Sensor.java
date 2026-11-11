package br.com.fiap.citizentech.sensor.domain;

import br.com.fiap.citizentech.croparea.domain.CropArea;
import br.com.fiap.citizentech.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Entity
@Table(name = "sensors")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sensor extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crop_area_id", nullable = false)
    private CropArea cropArea;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "device_code", nullable = false, length = 80)
    private String deviceCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "sensor_type", nullable = false, length = 30)
    private SensorType sensorType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SensorStatus status;

    @Column(name = "installation_date", nullable = false)
    private LocalDate installationDate;

    @Column(length = 500)
    private String description;
    @Column(length=255) @Getter(lombok.AccessLevel.NONE)
    private String deviceKeyHash;

    public void configureDeviceKey(String hash) { deviceKeyHash = hash; }
    public String deviceKeyHash() { return deviceKeyHash; }

    public Sensor(CropArea cropArea, String name, String deviceCode, SensorType sensorType,
                  LocalDate installationDate, String description) {
        this.cropArea = cropArea;
        this.name = name;
        this.deviceCode = deviceCode;
        this.sensorType = sensorType;
        this.status = SensorStatus.ACTIVE;
        this.installationDate = installationDate == null ? LocalDate.now() : installationDate;
        this.description = description;
    }

    public void update(CropArea cropArea, String name, String deviceCode, SensorType sensorType,
                       SensorStatus status, LocalDate installationDate, String description) {
        this.cropArea = cropArea;
        this.name = name;
        if (deviceCode != null) this.deviceCode = deviceCode;
        this.sensorType = sensorType;
        if (status != null) this.status = status;
        if (installationDate != null) this.installationDate = installationDate;
        this.description = description;
    }
}
