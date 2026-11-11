package br.com.fiap.citizentech.croparea.domain;

import br.com.fiap.citizentech.farm.domain.Farm;
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

import java.math.BigDecimal;

@Getter
@Entity
@Table(name = "crop_areas")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CropArea extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farm_id", nullable = false)
    private Farm farm;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "crop_type", nullable = false, length = 80)
    private String cropType;

    @Column(name = "area_size", nullable = false, precision = 12, scale = 2)
    private BigDecimal areaSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "area_unit", nullable = false, length = 20)
    private AreaUnit areaUnit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CropAreaStatus status;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(length = 500)
    private String description;

    public CropArea(Farm farm, String name, String cropType, BigDecimal areaSize, AreaUnit areaUnit,
                    BigDecimal latitude, BigDecimal longitude, String description) {
        this.farm = farm;
        this.name = name;
        this.cropType = cropType;
        this.areaSize = areaSize;
        this.areaUnit = areaUnit;
        this.latitude = latitude;
        this.longitude = longitude;
        this.description = description;
        this.status = CropAreaStatus.NORMAL;
    }

    public void archive(boolean archived) { this.status = archived ? CropAreaStatus.ARCHIVED : CropAreaStatus.NORMAL; }

    public void update(Farm farm, String name, String cropType, BigDecimal areaSize, AreaUnit areaUnit,
                       BigDecimal latitude, BigDecimal longitude, String description) {
        this.farm = farm;
        this.name = name;
        this.cropType = cropType;
        this.areaSize = areaSize;
        this.areaUnit = areaUnit;
        this.latitude = latitude;
        this.longitude = longitude;
        this.description = description;
    }
}
