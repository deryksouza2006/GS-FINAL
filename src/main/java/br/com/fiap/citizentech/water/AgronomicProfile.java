package br.com.fiap.citizentech.water;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import br.com.fiap.citizentech.croparea.domain.CropArea;
import br.com.fiap.citizentech.shared.domain.BaseEntity;

@Entity @Table(name="agronomic_profiles") @Getter @NoArgsConstructor
public class AgronomicProfile extends BaseEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="crop_area_id") private CropArea cropArea;
    @Column(nullable=false) private long revision;
    @Column(nullable=false,length=100) private String soilTexture;
    @Column(nullable=false) private double fieldCapacityPercent;
    @Column(nullable=false) private double wiltingPointPercent;
    private Double rootDepthCentimeters;
    private Double sensorDepthCentimeters;
    @Column(nullable=false,length=500) private String source;
    @Column(nullable=false) private boolean demo;
    public AgronomicProfile(CropArea area,long revision,ProfileRequest p) {
        this.cropArea=area; this.revision=revision; soilTexture=p.soilTexture();
        fieldCapacityPercent=p.fieldCapacityPercent(); wiltingPointPercent=p.wiltingPointPercent();
        rootDepthCentimeters=p.rootDepthCentimeters(); sensorDepthCentimeters=p.sensorDepthCentimeters();
        source=p.source(); demo=p.demo();
    }
}
