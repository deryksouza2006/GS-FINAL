package br.com.fiap.citizentech.water;
import br.com.fiap.citizentech.user.repository.UserRepository;
import br.com.fiap.citizentech.user.domain.UserRole;
import br.com.fiap.citizentech.farm.repository.FarmRepository;
import br.com.fiap.citizentech.sensor.repository.SensorRepository;
import br.com.fiap.citizentech.shared.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.Map;
@RestController @RequiredArgsConstructor
public class TechnicianController {
    private final UserRepository users;
    private final FarmRepository farms;
    private final SensorRepository sensors;
    private final br.com.fiap.citizentech.croparea.repository.CropAreaRepository cropAreas;
    private final JdbcTemplate jdbc;
    public record Maintenance(@NotNull Boolean maintenance,@PastOrPresent LocalDate calibratedAt,@NotBlank @Size(max=500) String note) { }
    private BusinessException missing() { return new BusinessException(HttpStatus.NOT_FOUND,"Registro não encontrado"); }
    @PutMapping("/api/farms/{farmId}/technicians/{technicianId}") @PreAuthorize("hasRole('PRODUCER')") @Transactional
    public Map<String,Object> assign(@AuthenticationPrincipal Jwt jwt,@PathVariable Long farmId,@PathVariable Long technicianId) {
        var owner=users.findByEmailIgnoreCase(jwt.getSubject()).orElseThrow(this::missing);
        if(!owner.isEnabled() || owner.getRole()!=UserRole.PRODUCER) throw new BusinessException(HttpStatus.FORBIDDEN,"Acesso negado");
        farms.findOwnedForUpdate(farmId,owner.getId()).orElseThrow(this::missing);
        var tech=users.findById(technicianId).filter(u->u.isEnabled() && u.getRole()==UserRole.TECHNICIAN).orElseThrow(this::missing);
        jdbc.update("INSERT INTO farm_technicians(farm_id,technician_id) VALUES (?,?) ON CONFLICT DO NOTHING",farmId,tech.getId());
        return Map.of("farmId",farmId,"technicianId",technicianId);
    }
    @PatchMapping("/api/technician/sensors/{id}/maintenance") @PreAuthorize("hasRole('TECHNICIAN')") @Transactional
    public Map<String,Object> maintain(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id,@Valid @RequestBody Maintenance request) {
        var sensor=sensors.lockDevice(id).orElseThrow(this::missing);
        requireAssignment(jwt.getSubject(), sensor.getCropArea().getFarm().getId());
        jdbc.update("UPDATE sensors SET status=?, calibrated_at=?, maintenance_note=?, updated_at=CURRENT_TIMESTAMP WHERE id=?",
                request.maintenance()?"MAINTENANCE":"ACTIVE",request.calibratedAt(),request.note(),id);
        return Map.of("sensorId",id,"status",request.maintenance()?"MAINTENANCE":"ACTIVE");
    }

    @PostMapping("/api/technician/sensors")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TECHNICIAN')")
    @Transactional
    public br.com.fiap.citizentech.sensor.dto.SensorResponse install(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody br.com.fiap.citizentech.sensor.dto.CreateSensorRequest request) {
        var crop = cropAreas.findById(request.cropAreaId()).orElseThrow(this::missing);
        requireAssignment(jwt.getSubject(), crop.getFarm().getId());
        if (crop.getStatus() == br.com.fiap.citizentech.croparea.domain.CropAreaStatus.ARCHIVED) {
            throw new BusinessException(HttpStatus.CONFLICT, "Cultivo arquivado");
        }
        try {
            return br.com.fiap.citizentech.sensor.dto.SensorResponse.from(sensors.saveAndFlush(
                    new br.com.fiap.citizentech.sensor.domain.Sensor(crop, request.name(), request.deviceCode(),
                            request.sensorType(), request.installationDate(), request.description())));
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw new BusinessException(HttpStatus.CONFLICT, "Código do sensor já utilizado ou cultivo indisponível");
        }
    }

    private void requireAssignment(String email, Long farmId) {
        var tech = users.findByEmailIgnoreCase(email).orElseThrow(this::missing);
        if (!tech.isEnabled() || tech.getRole() != UserRole.TECHNICIAN) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Acesso negado");
        }
        Boolean assigned = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM farm_technicians WHERE farm_id=? AND technician_id=?)",
                Boolean.class, farmId, tech.getId());
        if (!Boolean.TRUE.equals(assigned)) throw missing();
    }
}
