package br.com.fiap.citizentech.croparea.service;

import br.com.fiap.citizentech.croparea.domain.CropArea;
import br.com.fiap.citizentech.croparea.dto.CropAreaPageResponse;
import br.com.fiap.citizentech.croparea.dto.CropAreaRequest;
import br.com.fiap.citizentech.croparea.dto.CropAreaResponse;
import br.com.fiap.citizentech.croparea.repository.CropAreaRepository;
import br.com.fiap.citizentech.farm.domain.Farm;
import br.com.fiap.citizentech.farm.repository.FarmRepository;
import br.com.fiap.citizentech.shared.exception.BusinessException;
import br.com.fiap.citizentech.sensor.repository.SensorRepository;
import br.com.fiap.citizentech.user.domain.User;
import br.com.fiap.citizentech.user.domain.UserRole;
import br.com.fiap.citizentech.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@PreAuthorize("hasRole('PRODUCER')")
public class CropAreaService {
    private final CropAreaRepository cropAreas;
    private final FarmRepository farms;
    private final UserRepository users;
    private final CropAreaCapacityService capacity;
    private final SensorRepository sensors;
    private final br.com.fiap.citizentech.activity.ActivityRepository activities;
    private final br.com.fiap.citizentech.water.ProfileRepository profiles;
    private final br.com.fiap.citizentech.water.RiskJobRepository riskJobs;

    @Transactional
    public CropAreaResponse archive(String email, Long id, boolean archived) {
        CropArea area = cropAreas.findOwnedForUpdate(id, producer(email).getId()).orElseThrow(this::areaNotFound);
        area.archive(archived);
        return CropAreaResponse.from(cropAreas.saveAndFlush(area));
    }

    @Transactional
    public CropAreaResponse create(String email, CropAreaRequest request) {
        Farm farm = lockedFarm(request.farmId(), producer(email).getId());
        capacity.checkAvailable(farm, request.areaSize(), request.areaUnit(), null);
        CropArea area = new CropArea(farm, request.name(), request.cropType(), request.areaSize(),
                request.areaUnit(), request.latitude(), request.longitude(), request.description());
        return CropAreaResponse.from(cropAreas.saveAndFlush(area));
    }

    public CropAreaPageResponse list(String email, int page, int size) {
        return CropAreaPageResponse.from(cropAreas.findAllByFarmOwnerId(producer(email).getId(),
                PageRequest.of(page, size, Sort.by("id"))).map(CropAreaResponse::from));
    }

    public CropAreaPageResponse listByFarm(String email, Long farmId, int page, int size) {
        Long ownerId = producer(email).getId();
        farms.findByIdAndOwnerId(farmId, ownerId).orElseThrow(this::farmNotFound);
        return CropAreaPageResponse.from(cropAreas.findAllByFarmIdAndFarmOwnerId(farmId, ownerId,
                PageRequest.of(page, size, Sort.by("id"))).map(CropAreaResponse::from));
    }

    public CropAreaResponse get(String email, Long id) {
        return CropAreaResponse.from(cropAreas.findByIdAndFarmOwnerId(id, producer(email).getId())
                .orElseThrow(this::areaNotFound));
    }

    @Transactional
    public CropAreaResponse update(String email, Long id, CropAreaRequest request) {
        Long ownerId = producer(email).getId();
        CropArea area = cropAreas.findOwnedForUpdate(id, ownerId).orElseThrow(this::areaNotFound);
        Long oldFarmId = area.getFarm().getId();
        List<Long> ids = oldFarmId.equals(request.farmId()) ? List.of(oldFarmId) : List.of(oldFarmId, request.farmId());
        // Ordem única para que transferências entre as mesmas propriedades não invertam os bloqueios.
        List<Farm> locked = farms.findOwnedForUpdate(ids, ownerId);
        if (locked.size() != ids.size()) {
            throw farmNotFound();
        }
        Farm target = locked.stream().filter(farm -> farm.getId().equals(request.farmId())).findFirst().orElseThrow();
        capacity.checkAvailable(target, request.areaSize(), request.areaUnit(), id);
        area.update(target, request.name(), request.cropType(), request.areaSize(), request.areaUnit(),
                request.latitude(), request.longitude(), request.description());
        return CropAreaResponse.from(cropAreas.saveAndFlush(area));
    }

    @Transactional
    public void delete(String email, Long id) {
        Long ownerId = producer(email).getId();
        CropArea area = cropAreas.findOwnedForUpdate(id, ownerId).orElseThrow(this::areaNotFound);
        if (activities.existsByCropAreaId(id) || profiles.existsByCropAreaId(id) || riskJobs.existsByCropAreaId(id)) {
            throw new BusinessException(HttpStatus.CONFLICT,"Cultivo com histórico deve ser arquivado");
        }
        if (sensors.existsByCropAreaId(id)) {
            throw linkedSensors();
        }
        lockedFarm(area.getFarm().getId(), ownerId);
        try {
            cropAreas.delete(area);
            cropAreas.flush();
        } catch (DataIntegrityViolationException ex) {
            if (ex.getCause() instanceof ConstraintViolationException constraint
                    && "fk_sensors_crop_area".equals(constraint.getConstraintName())) {
                throw linkedSensors();
            }
            throw ex;
        }
    }

    private Farm lockedFarm(Long id, Long ownerId) {
        return farms.findOwnedForUpdate(id, ownerId).orElseThrow(this::farmNotFound);
    }

    private User producer(String email) {
        User user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
        if (!user.isEnabled() || user.getRole() != UserRole.PRODUCER) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Acesso permitido somente a produtores ativos");
        }
        return user;
    }

    private BusinessException farmNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "Propriedade não encontrada");
    }

    private BusinessException areaNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "Área de cultivo não encontrada");
    }

    private BusinessException linkedSensors() {
        return new BusinessException(HttpStatus.CONFLICT,
                "Não é possível excluir área de cultivo com sensores vinculados");
    }
}
