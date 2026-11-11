package br.com.fiap.citizentech.activity;

import br.com.fiap.citizentech.croparea.domain.CropArea;
import br.com.fiap.citizentech.shared.domain.BaseEntity;
import br.com.fiap.citizentech.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.math.BigDecimal;

@Entity @Table(name="field_activities") @Getter @NoArgsConstructor
public class FieldActivity extends BaseEntity {
    public enum Type { IRRIGATION, FERTILIZATION, PLANTING, HARVEST }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="crop_area_id") private CropArea cropArea;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="author_id") private User author;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) private Type type;
    @Column(nullable=false) private Instant performedAt;
    @Column(length=2000) private String description;
    @Column(precision=14, scale=3) private BigDecimal quantity;
    @Column(length=30) private String unit;
    public FieldActivity(CropArea area, User author, ActivityRequest request) {
        this.cropArea=area; this.author=author; update(request);
    }
    public void update(ActivityRequest request) {
        type=request.type(); performedAt=request.performedAt(); description=request.description();
        quantity=request.quantity(); unit=request.unit();
    }
}
