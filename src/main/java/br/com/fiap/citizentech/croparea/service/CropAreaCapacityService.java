package br.com.fiap.citizentech.croparea.service;

import br.com.fiap.citizentech.croparea.domain.AreaUnit;
import br.com.fiap.citizentech.croparea.repository.CropAreaRepository;
import br.com.fiap.citizentech.farm.domain.Farm;
import br.com.fiap.citizentech.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class CropAreaCapacityService {
    private final CropAreaRepository cropAreas;

    // O chamador mantém a Farm bloqueada até concluir a transação de escrita.
    public void checkAvailable(Farm farm, BigDecimal area, AreaUnit unit, Long excludedId) {
        if (farm.getTotalArea() != null) {
            BigDecimal occupied = cropAreas.sumSquareMeters(farm.getId(), excludedId);
            checkLimit(occupied.add(unit.toSquareMeters(area)),
                    farm.getAreaUnit().toSquareMeters(farm.getTotalArea()));
        }
    }

    public void checkFarmTotal(Long farmId, BigDecimal totalArea, AreaUnit unit) {
        checkLimit(cropAreas.sumSquareMeters(farmId, null), unit.toSquareMeters(totalArea));
    }

    private void checkLimit(BigDecimal occupied, BigDecimal total) {
        if (occupied.compareTo(total) > 0) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "A soma das áreas de cultivo ultrapassa a área total da propriedade");
        }
    }
}
