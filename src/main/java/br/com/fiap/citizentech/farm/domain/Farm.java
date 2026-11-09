package br.com.fiap.citizentech.farm.domain;

import br.com.fiap.citizentech.shared.domain.BaseEntity;
import br.com.fiap.citizentech.croparea.domain.AreaUnit;
import br.com.fiap.citizentech.user.domain.User;
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
@Table(name = "farms")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Farm extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "responsible_name", nullable = false, length = 100)
    private String responsibleName;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 2)
    private String state;

    @Column(nullable = false, length = 80)
    private String country;

    @Column(name = "total_area", precision = 12, scale = 2)
    private BigDecimal totalArea;

    @Enumerated(EnumType.STRING)
    @Column(name = "area_unit", length = 20)
    private AreaUnit areaUnit;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    public Farm(User owner, String name, String responsibleName, String city, String state,
                String country, BigDecimal latitude, BigDecimal longitude) {
        this.owner = owner;
        this.name = name;
        this.responsibleName = responsibleName;
        this.city = city;
        this.state = state;
        this.country = country;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Farm(User owner, String name, String city, String state, BigDecimal totalArea, AreaUnit areaUnit) {
        this(owner, name, owner.getName(), city, state, "Brasil", null, null);
        this.totalArea = totalArea;
        this.areaUnit = areaUnit;
    }

    public void update(String name, String city, String state, BigDecimal totalArea, AreaUnit areaUnit) {
        this.name = name;
        this.city = city;
        this.state = state;
        this.totalArea = totalArea;
        this.areaUnit = areaUnit;
    }
}
