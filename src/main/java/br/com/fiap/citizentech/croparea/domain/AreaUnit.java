package br.com.fiap.citizentech.croparea.domain;

import java.math.BigDecimal;

public enum AreaUnit {
    HECTARE,
    SQUARE_METER;

    public BigDecimal toSquareMeters(BigDecimal area) {
        return switch (this) {
            case HECTARE -> area.multiply(BigDecimal.valueOf(10_000));
            case SQUARE_METER -> area;
        };
    }
}
