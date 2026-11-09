package br.com.fiap.citizentech.farm.service;

import br.com.fiap.citizentech.croparea.repository.CropAreaRepository;
import br.com.fiap.citizentech.croparea.service.CropAreaCapacityService;
import br.com.fiap.citizentech.farm.domain.Farm;
import br.com.fiap.citizentech.farm.dto.FarmPageResponse;
import br.com.fiap.citizentech.farm.dto.FarmRequest;
import br.com.fiap.citizentech.farm.dto.FarmResponse;
import br.com.fiap.citizentech.farm.repository.FarmRepository;
import br.com.fiap.citizentech.shared.exception.BusinessException;
import br.com.fiap.citizentech.user.domain.User;
import br.com.fiap.citizentech.user.domain.UserRole;
import br.com.fiap.citizentech.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@PreAuthorize("hasRole('PRODUCER')")
public class FarmService {
    private final FarmRepository farms;
    private final UserRepository users;
    private final CropAreaRepository cropAreas;
    private final CropAreaCapacityService capacity;

    @Transactional
    public FarmResponse create(String email, FarmRequest request) {
        User owner = producer(email);
        Farm farm = new Farm(owner, request.name(), request.city(), request.state(),
                request.totalArea(), request.areaUnit());
        return FarmResponse.from(farms.save(farm));
    }

    public FarmPageResponse list(String email, int page, int size) {
        User owner = producer(email);
        return FarmPageResponse.from(farms.findAllByOwnerId(owner.getId(),
                PageRequest.of(page, size, Sort.by("id"))).map(FarmResponse::from));
    }

    public FarmResponse get(String email, Long id) {
        return FarmResponse.from(ownedFarm(id, producer(email).getId()));
    }

    @Transactional
    public FarmResponse update(String email, Long id, FarmRequest request) {
        Farm farm = farms.findOwnedForUpdate(id, producer(email).getId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Propriedade não encontrada"));
        capacity.checkFarmTotal(farm.getId(), request.totalArea(), request.areaUnit());
        farm.update(request.name(), request.city(), request.state(), request.totalArea(), request.areaUnit());
        return FarmResponse.from(farm);
    }

    @Transactional
    public void delete(String email, Long id) {
        Farm farm = farms.findOwnedForUpdate(id, producer(email).getId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Propriedade não encontrada"));
        if (cropAreas.existsByFarmId(farm.getId())) {
            throw linkedCropAreas();
        }
        try {
            farms.delete(farm);
            farms.flush();
        } catch (DataIntegrityViolationException ex) {
            // A chave estrangeira protege também contra um vínculo criado durante a exclusão.
            if (ex.getCause() instanceof ConstraintViolationException constraint
                    && "crop_areas_farm_id_fkey".equals(constraint.getConstraintName())) {
                throw linkedCropAreas();
            }
            throw ex;
        }
    }

    private User producer(String email) {
        User user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
        if (!user.isEnabled() || user.getRole() != UserRole.PRODUCER) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Acesso permitido somente a produtores ativos");
        }
        return user;
    }

    private Farm ownedFarm(Long id, Long ownerId) {
        return farms.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Propriedade não encontrada"));
    }

    private BusinessException linkedCropAreas() {
        return new BusinessException(HttpStatus.CONFLICT,
                "Não é possível excluir propriedade com áreas de cultivo vinculadas");
    }
}
