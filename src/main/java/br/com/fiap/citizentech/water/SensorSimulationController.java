package br.com.fiap.citizentech.water;
import br.com.fiap.citizentech.sensorreading.domain.SensorReading;
import br.com.fiap.citizentech.sensorreading.dto.ReadingResponse;
import br.com.fiap.citizentech.sensorreading.repository.SensorReadingRepository;
import br.com.fiap.citizentech.sensor.repository.SensorRepository;
import br.com.fiap.citizentech.croparea.repository.CropAreaRepository;
import br.com.fiap.citizentech.croparea.domain.CropAreaStatus;
import br.com.fiap.citizentech.user.repository.UserRepository;
import br.com.fiap.citizentech.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;

@RestController @RequiredArgsConstructor @PreAuthorize("hasRole('PRODUCER')")
@ConditionalOnProperty(name="app.demo.enabled",havingValue="true")
public class SensorSimulationController {
    public enum Scenario { NORMAL, ATTENTION, CRITICAL, INSUFFICIENT_DATA }
    public record Request(@NotNull Scenario scenario) { }
    private final UserRepository users;
    private final CropAreaRepository areas;
    private final SensorRepository sensors;
    private final SensorReadingRepository readings;
    @PostMapping("/api/crop-areas/{id}/sensor-readings/simulate") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public List<ReadingResponse> simulate(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id,@Valid @RequestBody Request body) {
        var user=users.findByEmailIgnoreCase(jwt.getSubject()).orElseThrow(()->new BusinessException(HttpStatus.NOT_FOUND,"Usuário ausente"));
        if(!user.isEnabled() || user.getRole()!=br.com.fiap.citizentech.user.domain.UserRole.PRODUCER)
            throw new BusinessException(HttpStatus.FORBIDDEN,"Acesso negado");
        var area=areas.findOwnedForUpdate(id,user.getId()).orElseThrow(()->new BusinessException(HttpStatus.NOT_FOUND,"Cultivo ausente"));
        if(area.getStatus()==CropAreaStatus.ARCHIVED) throw new BusinessException(HttpStatus.CONFLICT,"Cultivo arquivado");
        var available=sensors.findAllByCropAreaIdAndCropAreaFarmOwnerId(id,user.getId(),PageRequest.of(0,100)).stream()
                .filter(s->s.getStatus()==br.com.fiap.citizentech.sensor.domain.SensorStatus.ACTIVE).toList();
        if(available.isEmpty()) throw new BusinessException(HttpStatus.CONFLICT,"Cadastre um sensor ativo");
        int soil=switch(body.scenario()) { case NORMAL -> 28; case ATTENTION -> 19; case CRITICAL -> 14; case INSUFFICIENT_DATA -> 24; };
        Instant time=body.scenario()==Scenario.INSUFFICIENT_DATA ? Instant.now().minusSeconds(172800) : Instant.now();
        return available.stream().map(s-> {
            var type = s.getSensorType();
            var r=SensorReading.simulated(s,
                    type==br.com.fiap.citizentech.sensor.domain.SensorType.TEMPERATURE || type==br.com.fiap.citizentech.sensor.domain.SensorType.WEATHER_STATION ? BigDecimal.valueOf(25) : null,
                    type==br.com.fiap.citizentech.sensor.domain.SensorType.HUMIDITY || type==br.com.fiap.citizentech.sensor.domain.SensorType.WEATHER_STATION ? BigDecimal.valueOf(65) : null,
                    type==br.com.fiap.citizentech.sensor.domain.SensorType.SOIL_MOISTURE || type==br.com.fiap.citizentech.sensor.domain.SensorType.WEATHER_STATION ? BigDecimal.valueOf(soil) : null,time);
            // These readings await an explicit risk-job request, rather than legacy automatic AI analysis.
            return ReadingResponse.from(readings.saveAndFlush(r));
        }).toList();
    }
}
