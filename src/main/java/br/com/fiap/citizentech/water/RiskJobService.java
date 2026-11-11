package br.com.fiap.citizentech.water;
import br.com.fiap.citizentech.croparea.repository.CropAreaRepository;
import br.com.fiap.citizentech.croparea.domain.CropAreaStatus;
import br.com.fiap.citizentech.sensorreading.repository.SensorReadingRepository;
import br.com.fiap.citizentech.shared.outbox.*;
import br.com.fiap.citizentech.shared.exception.BusinessException;
import br.com.fiap.citizentech.user.repository.UserRepository;
import br.com.fiap.citizentech.user.domain.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.UUID;

@Service @RequiredArgsConstructor @Transactional(readOnly=true) @PreAuthorize("hasRole('PRODUCER')")
public class RiskJobService {
    private final RiskJobRepository jobs;
    private final CropAreaRepository areas;
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final SensorReadingRepository readings;
    private final OutboxRepository outbox;
    private final ObjectMapper mapper;
    private final WaterRiskCalculator.Thresholds thresholds;
    private Long owner(String email) {
        var u=users.findByEmailIgnoreCase(email).orElseThrow(this::missing);
        if(!u.isEnabled() || u.getRole()!=UserRole.PRODUCER) throw new BusinessException(HttpStatus.FORBIDDEN,"Acesso negado");
        return u.getId();
    }
    private BusinessException missing() { return new BusinessException(HttpStatus.NOT_FOUND,"Registro não encontrado"); }
    @Transactional public RiskJob create(String email,Long area) {
        Long owner=owner(email);
        var crop=areas.findOwnedForUpdate(area,owner).orElseThrow(this::missing);
        if(crop.getStatus()==CropAreaStatus.ARCHIVED) throw new BusinessException(HttpStatus.CONFLICT,"Cultivo arquivado");
        var now=Instant.now();
        var snapshot=readings.findAllBySensorCropAreaIdAndSensorCropAreaFarmOwnerId(area,owner,
                PageRequest.of(0,500,Sort.by(Sort.Direction.DESC,"measuredAt","id"))).stream()
                .filter(r->r.getSensor().getStatus()==br.com.fiap.citizentech.sensor.domain.SensorStatus.ACTIVE)
                .map(r->new WaterRiskCalculator.Measurement(r.getId(),r.getSensor().getId(),r.getMeasuredAt(),
                        number(r.getSoilMoisturePercent()),number(r.getTemperatureCelsius()),number(r.getAirHumidityPercent()),
                        "SIMULATED".equals(r.getSource().name()))).toList();
        var job=jobs.saveAndFlush(new RiskJob(area,owner,profiles.findFirstByCropAreaIdOrderByRevisionDesc(area).orElse(null),snapshot,now));
        var event=UUID.randomUUID();
        job.captureCropType(crop.getCropType());
        job.captureThresholds(thresholds);
        var rainRows=readings.findAllBySensorCropAreaIdAndSensorCropAreaFarmOwnerId(area,owner,
                PageRequest.of(0,1000,Sort.by(Sort.Direction.DESC,"measuredAt","id"))).stream()
                .filter(r->r.getRainfallMillimeters()!=null && r.getRainfallIntervalStart()!=null
                        && !r.getMeasuredAt().isAfter(now))
                .map(r->new RainfallCalculator.Interval(r.getSensor().getId(),r.getRainfallIntervalStart(),
                        r.getMeasuredAt(),r.getRainfallMillimeters().doubleValue())).toList();
        long rainSensor=rainRows.isEmpty() ? -1 : rainRows.getFirst().sensorId();
        job.captureRainfall(java.util.Map.of(
                "last24Hours",RainfallCalculator.accumulate(rainRows,rainSensor,now.minusSeconds(86400),now),
                "last7Days",RainfallCalculator.accumulate(rainRows,rainSensor,now.minusSeconds(604800),now)),
                rainRows.stream().filter(r->r.sensorId()==rainSensor && r.millimeters()>0)
                        .map(RainfallCalculator.Interval::end).findFirst().orElse(null));
        outbox.save(new OutboxEvent(event,"RiskAnalysisJob",job.getId(),"RiskAnalysisRequested",mapper.writeValueAsString(java.util.Map.of("jobId",job.getId()))));
        return job;
    }
    private Double number(java.math.BigDecimal v) { return v==null ? null : v.doubleValue(); }
    @Transactional public RiskJob saveGuidance(String email,Long id,java.util.Map<String,Object> guidance) {
        Long owner=owner(email);
        var job=jobs.lock(id).filter(j->j.getOwnerId().equals(owner)).orElseThrow(this::missing);
        job.guidance(guidance); return job;
    }
    public RiskJob get(String email,Long id) { return jobs.findByIdAndOwnerId(id,owner(email)).orElseThrow(this::missing); }
    public RiskJob alert(String email, Long id) {
        var job = get(email, id);
        if (job.getAlertStatus() == null) throw missing();
        return job;
    }
    public Page<RiskJob> cropAlerts(String email, Long area, int page, int size) {
        Long ownerId = owner(email);
        areas.findByIdAndFarmOwnerId(area, ownerId).orElseThrow(this::missing);
        return jobs.findAllByCropAreaIdAndOwnerIdAndAlertStatusIsNotNull(area, ownerId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
    }
    public Page<RiskJob> list(String email,Long area,int page,int size) {
        Long owner=owner(email); areas.findByIdAndFarmOwnerId(area,owner).orElseThrow(this::missing);
        return jobs.findAllByCropAreaIdAndOwnerId(area,owner,PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"id")));
    }
    public Page<RiskJob> alerts(String email,int page,int size) {
        return jobs.findAllByOwnerIdAndAlertStatusIsNotNull(owner(email),PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"id")));
    }
    public Page<RiskJob> openAlerts(String email) {
        return jobs.findAllByOwnerIdAndAlertStatusIn(owner(email), java.util.List.of("OPEN","ACKNOWLEDGED"),
                PageRequest.of(0,20,Sort.by(Sort.Direction.DESC,"id")));
    }
    @Transactional public RiskJob transition(String email,Long id,boolean resolve) {
        Long owner=owner(email); var job=jobs.lock(id).filter(j->j.getOwnerId().equals(owner)).orElseThrow(this::missing);
        if(job.getAlertStatus()==null) throw missing();
        if(resolve) job.resolve(); else job.acknowledge(); return job;
    }
}
