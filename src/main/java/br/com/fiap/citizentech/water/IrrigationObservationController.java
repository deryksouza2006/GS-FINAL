package br.com.fiap.citizentech.water;
import br.com.fiap.citizentech.activity.*;
import br.com.fiap.citizentech.sensorreading.repository.SensorReadingRepository;
import br.com.fiap.citizentech.sensorreading.domain.SensorReading;
import br.com.fiap.citizentech.user.repository.UserRepository;
import br.com.fiap.citizentech.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.*;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
@RestController @RequiredArgsConstructor
public class IrrigationObservationController {
    private final ActivityService activities;
    private final SensorReadingRepository readings;
    private final UserRepository users;
    public record Observation(String status,Long beforeReadingId,Long afterReadingId,Double changePercentagePoints,String message) { }
    @GetMapping("/api/crop-areas/{cropAreaId}/activities/{activityId}/irrigation-observation") @Transactional(readOnly=true)
    public Observation observe(@AuthenticationPrincipal Jwt jwt,@PathVariable Long cropAreaId,@PathVariable Long activityId) {
        var activity=activities.get(jwt.getSubject(),cropAreaId,activityId);
        if(activity.type()!=FieldActivity.Type.IRRIGATION) throw new BusinessException(HttpStatus.CONFLICT,"Atividade não é irrigação");
        Long owner=users.findByEmailIgnoreCase(jwt.getSubject()).orElseThrow().getId();
        var samples=readings.findAllBySensorCropAreaIdAndSensorCropAreaFarmOwnerId(cropAreaId,owner,
                PageRequest.of(0,500,Sort.by(Sort.Direction.DESC,"measuredAt","id"))).stream()
                .filter(r->r.getSoilMoisturePercent()!=null && !r.getMeasuredAt().isAfter(Instant.now())).toList();
        var before=samples.stream().filter(r->!r.getMeasuredAt().isAfter(activity.performedAt())
                && !r.getMeasuredAt().isBefore(activity.performedAt().minusSeconds(21600))).findFirst();
        if(before.isEmpty()) return missing();
        var after=samples.stream().filter(r->r.getSensor().getId().equals(before.get().getSensor().getId())
                && r.getMeasuredAt().isAfter(activity.performedAt()) && !r.getMeasuredAt().isAfter(activity.performedAt().plusSeconds(21600)))
                .min(Comparator.comparing(SensorReading::getMeasuredAt).thenComparing(SensorReading::getId));
        if(after.isEmpty()) return new Observation("AWAITING_READING",before.get().getId(),null,null,
                "Aguardando leitura posterior do mesmo sensor na janela de seis horas");
        double change=after.get().getSoilMoisturePercent().subtract(before.get().getSoilMoisturePercent()).doubleValue();
        return new Observation(change >= 1 ? "OBSERVED" : "BELOW_DEMO_THRESHOLD",before.get().getId(),after.get().getId(),change,
                "Variação observada no mesmo sensor; não comprova eficácia nem falha da irrigação");
    }
    private Observation missing() { return new Observation("INSUFFICIENT_DATA",null,null,null,"Leituras antes/depois insuficientes na janela de seis horas"); }
}
