package br.com.fiap.citizentech.farm.dto;

import br.com.fiap.citizentech.croparea.domain.AreaUnit;
import br.com.fiap.citizentech.farm.domain.Farm;

import java.math.BigDecimal;

public record FarmResponse(Long id, String name, String city, String state,
                           BigDecimal totalArea, AreaUnit areaUnit) {
    public static FarmResponse from(Farm farm) {
        return new FarmResponse(farm.getId(), farm.getName(), farm.getCity(), farm.getState(),
                farm.getTotalArea(), farm.getAreaUnit());
    }
}
