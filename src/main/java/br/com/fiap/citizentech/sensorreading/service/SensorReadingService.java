package br.com.fiap.citizentech.sensorreading.service;

import br.com.fiap.citizentech.croparea.repository.CropAreaRepository;
import br.com.fiap.citizentech.sensor.domain.SensorStatus;
import br.com.fiap.citizentech.sensor.repository.SensorRepository;
import br.com.fiap.citizentech.sensorreading.domain.*;
import br.com.fiap.citizentech.sensorreading.dto.*;
import br.com.fiap.citizentech.sensorreading.event.SensorReadingCreated;
import br.com.fiap.citizentech.sensorreading.repository.SensorReadingRepository;
import br.com.fiap.citizentech.shared.exception.BusinessException;
import br.com.fiap.citizentech.shared.outbox.*;
import br.com.fiap.citizentech.user.domain.*;
import br.com.fiap.citizentech.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@PreAuthorize("hasRole('PRODUCER')")
public class SensorReadingService {
    private final SensorReadingRepository readings;
    private final SensorRepository sensors;
    private final CropAreaRepository cropAreas;
    private final UserRepository users;
    private final OutboxRepository outbox;
    private final ObjectMapper mapper;
    private final org.springframework.security.crypto.password.PasswordEncoder deviceEncoder;

    @Transactional
    public String rotateDeviceKey(String email, Long sensorId) {
        var sensor = sensors.findOwnedForUpdate(sensorId, producer(email).getId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Sensor não encontrado"));
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        String key = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sensor.configureDeviceKey(deviceEncoder.encode(key));
        return key;
    }

    @PreAuthorize("permitAll()")
    @Transactional
    public ReadingResponse ingestDevice(String key, CreateReadingRequest request) {
        var sensor = sensors.lockDevice(request.sensorId()).orElse(null);
        if (sensor == null || key == null || key.length() != 43 || sensor.deviceKeyHash() == null
                || !deviceEncoder.matches(key, sensor.deviceKeyHash())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Credencial do sensor inválida");
        }
        var owner = sensor.getCropArea().getFarm().getOwner();
        if (!owner.isEnabled() || owner.getRole() != UserRole.PRODUCER) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Sensor indisponível");
        }
        return recordReading(sensor, owner.getId(), request, ReadingSource.DEVICE);
    }
    private final br.com.fiap.citizentech.analysis.repository.AgronomicAnalysisRepository analyses;

    @Transactional
    public ReadingResponse create(String email, CreateReadingRequest request) {
        var sensor = sensors.findOwnedForUpdate(request.sensorId(), producer(email).getId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Sensor não encontrado"));
        return recordReading(sensor, sensor.getCropArea().getFarm().getOwner().getId(), request, ReadingSource.MANUAL);
    }

    private ReadingResponse recordReading(br.com.fiap.citizentech.sensor.domain.Sensor sensor, Long ownerId,
                                          CreateReadingRequest request, ReadingSource source) {
        var area = cropAreas.findOwnedForUpdate(sensor.getCropArea().getId(), ownerId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Cultivo não encontrado"));
        if (area.getStatus() == br.com.fiap.citizentech.croparea.domain.CropAreaStatus.ARCHIVED) {
            throw new BusinessException(HttpStatus.CONFLICT, "Cultivo arquivado");
        }
        if (sensor.getStatus() != SensorStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.CONFLICT, "Somente sensores ACTIVE podem registrar leituras");
        }
        if (request.measuredAt() != null && request.measuredAt().isAfter(Instant.now().plusSeconds(300))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Data da medição não pode exceder 5 minutos no futuro");
        }
        boolean compatible = switch (sensor.getSensorType()) {
            case SOIL_MOISTURE -> request.soilMoisturePercent() != null;
            case TEMPERATURE -> request.temperatureCelsius() != null;
            case HUMIDITY -> request.airHumidityPercent() != null;
            case WEATHER_STATION -> request.isMeasurementPresent();
        };
        if (!compatible) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Informe a medição obrigatória para o tipo do sensor");
        }
        var reading = new SensorReading(sensor, request.temperatureCelsius(), request.airHumidityPercent(),
                request.soilMoisturePercent(), request.batteryPercent(), request.measuredAt(), source);
        reading.rainfall(request.rainfallMillimeters(),request.rainfallIntervalStart());
        readings.saveAndFlush(reading);
        UUID eventId = UUID.randomUUID();
        var event = SensorReadingCreated.from(eventId, reading);
        outbox.saveAndFlush(new OutboxEvent(eventId, "SensorReading", reading.getId(), "SensorReadingCreated",
                mapper.writeValueAsString(event)));
        return ReadingResponse.from(reading);
    }

    public ReadingPageResponse list(String email, int page, int size) {
        return ReadingPageResponse.from(readings.findAllBySensorCropAreaFarmOwnerId(producer(email).getId(), pageable(page, size))
                .map(ReadingResponse::from));
    }

    public ReadingPageResponse listBySensor(String email, Long sensorId, int page, int size) {
        Long ownerId = producer(email).getId();
        sensors.findByIdAndCropAreaFarmOwnerId(sensorId, ownerId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Sensor não encontrado"));
        return ReadingPageResponse.from(readings.findAllBySensorIdAndSensorCropAreaFarmOwnerId(sensorId, ownerId, pageable(page, size))
                .map(ReadingResponse::from));
    }

    public ReadingPageResponse listByCropArea(String email, Long cropAreaId, int page, int size) {
        Long ownerId = producer(email).getId();
        ownedCrop(cropAreaId, ownerId);
        return ReadingPageResponse.from(readings.findAllBySensorCropAreaIdAndSensorCropAreaFarmOwnerId(cropAreaId, ownerId, pageable(page, size))
                .map(ReadingResponse::from));
    }

    public ReadingResponse latest(String email, Long cropAreaId) {
        Long ownerId = producer(email).getId();
        ownedCrop(cropAreaId, ownerId);
        return readings.findFirstBySensorCropAreaIdAndSensorCropAreaFarmOwnerIdOrderByMeasuredAtDescIdDesc(cropAreaId, ownerId)
                .map(ReadingResponse::from).orElseThrow(this::notFound);
    }

    public ReadingResponse get(String email, Long id) {
        return readings.findByIdAndSensorCropAreaFarmOwnerId(id, producer(email).getId())
                .map(ReadingResponse::from).orElseThrow(this::notFound);
    }

    @Transactional
    public void delete(String email, Long id) {
        var reading = readings.findOwnedForUpdate(id, producer(email).getId()).orElseThrow(this::notFound);
        if (reading.getAnalysisStatus() == ReadingAnalysisStatus.ANALYZED) {
            throw new BusinessException(HttpStatus.CONFLICT, "Leituras ANALYZED não podem ser excluídas");
        }
        outbox.cancelPending("SensorReading", id, "Leitura removida antes da publicação");
        analyses.findByReadingId(id).ifPresent(analyses::delete);
        analyses.flush();
        readings.delete(reading);
        readings.flush();
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("measuredAt"), Sort.Order.desc("id")));
    }

    private void ownedCrop(Long cropAreaId, Long ownerId) {
        cropAreas.findByIdAndFarmOwnerId(cropAreaId, ownerId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Área de cultivo não encontrada"));
    }

    private User producer(String email) {
        User user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
        if (!user.isEnabled() || user.getRole() != UserRole.PRODUCER) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Acesso permitido somente a produtores ativos");
        }
        return user;
    }

    private BusinessException notFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "Leitura não encontrada");
    }
}
