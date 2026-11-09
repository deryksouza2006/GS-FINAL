package br.com.fiap.citizentech.sensor.service;

import br.com.fiap.citizentech.croparea.domain.CropArea;
import br.com.fiap.citizentech.croparea.repository.CropAreaRepository;
import br.com.fiap.citizentech.sensor.domain.Sensor;
import br.com.fiap.citizentech.sensor.dto.CreateSensorRequest;
import br.com.fiap.citizentech.sensor.dto.SensorPageResponse;
import br.com.fiap.citizentech.sensor.dto.SensorResponse;
import br.com.fiap.citizentech.sensor.dto.UpdateSensorRequest;
import br.com.fiap.citizentech.sensor.repository.SensorRepository;
import br.com.fiap.citizentech.sensorreading.repository.SensorReadingRepository;
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
public class SensorService {
    private final SensorRepository sensors;
    private final CropAreaRepository cropAreas;
    private final UserRepository users;
    private final SensorReadingRepository readings;

    @Transactional
    public SensorResponse create(String email, CreateSensorRequest request) {
        CropArea cropArea = lockedCropArea(request.cropAreaId(), producer(email).getId());
        if (sensors.existsByDeviceCodeIgnoreCase(request.deviceCode())) {
            throw duplicateCode();
        }
        return save(new Sensor(cropArea, request.name(), request.deviceCode(), request.sensorType(),
                request.installationDate(), request.description()));
    }

    public SensorPageResponse list(String email, int page, int size) {
        return SensorPageResponse.from(sensors.findAllByCropAreaFarmOwnerId(producer(email).getId(),
                PageRequest.of(page, size, Sort.by("id"))).map(SensorResponse::from));
    }

    public SensorPageResponse listByCropArea(String email, Long cropAreaId, int page, int size) {
        Long ownerId = producer(email).getId();
        cropAreas.findByIdAndFarmOwnerId(cropAreaId, ownerId).orElseThrow(this::cropAreaNotFound);
        return SensorPageResponse.from(sensors.findAllByCropAreaIdAndCropAreaFarmOwnerId(cropAreaId, ownerId,
                PageRequest.of(page, size, Sort.by("id"))).map(SensorResponse::from));
    }

    public SensorResponse get(String email, Long id) {
        return SensorResponse.from(sensors.findByIdAndCropAreaFarmOwnerId(id, producer(email).getId())
                .orElseThrow(this::sensorNotFound));
    }

    @Transactional
    public SensorResponse update(String email, Long id, UpdateSensorRequest request) {
        Long ownerId = producer(email).getId();
        Sensor sensor = sensors.findOwnedForUpdate(id, ownerId).orElseThrow(this::sensorNotFound);
        CropArea target = lockedCropArea(request.cropAreaId(), ownerId);
        String code = request.deviceCode() == null ? sensor.getDeviceCode() : request.deviceCode();
        if (sensors.existsByDeviceCodeIgnoreCaseAndIdNot(code, id)) {
            throw duplicateCode();
        }
        sensor.update(target, request.name(), request.deviceCode(), request.sensorType(), request.status(),
                request.installationDate(), request.description());
        return save(sensor);
    }

    @Transactional
    public void delete(String email, Long id) {
        Sensor sensor = sensors.findOwnedForUpdate(id, producer(email).getId()).orElseThrow(this::sensorNotFound);
        if (readings.existsBySensorId(id)) {
            throw linkedReadings();
        }
        try {
            sensors.delete(sensor);
            sensors.flush();
        } catch (DataIntegrityViolationException ex) {
            if (ex.getCause() instanceof ConstraintViolationException constraint
                    && "fk_sensor_readings_sensor".equals(constraint.getConstraintName())) {
                throw linkedReadings();
            }
            throw ex;
        }
    }

    private SensorResponse save(Sensor sensor) {
        try {
            return SensorResponse.from(sensors.saveAndFlush(sensor));
        } catch (DataIntegrityViolationException ex) {
            // O índice único também protege requisições concorrentes que passaram pela consulta prévia.
            if (ex.getCause() instanceof ConstraintViolationException constraint
                    && "ux_sensors_device_code_upper".equals(constraint.getConstraintName())) {
                throw duplicateCode();
            }
            throw ex;
        }
    }

    private CropArea lockedCropArea(Long id, Long ownerId) {
        return cropAreas.findOwnedForUpdate(id, ownerId).orElseThrow(this::cropAreaNotFound);
    }

    private User producer(String email) {
        User user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
        if (!user.isEnabled() || user.getRole() != UserRole.PRODUCER) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Acesso permitido somente a produtores ativos");
        }
        return user;
    }

    private BusinessException duplicateCode() {
        return new BusinessException(HttpStatus.CONFLICT, "Código de dispositivo já cadastrado");
    }

    private BusinessException linkedReadings() {
        return new BusinessException(HttpStatus.CONFLICT, "Não é possível excluir sensor com leituras vinculadas");
    }

    private BusinessException cropAreaNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "Área de cultivo não encontrada");
    }

    private BusinessException sensorNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "Sensor não encontrado");
    }
}
