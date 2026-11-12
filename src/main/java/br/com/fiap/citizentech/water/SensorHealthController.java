package br.com.fiap.citizentech.water;
import br.com.fiap.citizentech.sensor.service.SensorService;
import br.com.fiap.citizentech.sensorreading.service.SensorReadingService;
import br.com.fiap.citizentech.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import java.time.*;
@RestController @RequiredArgsConstructor
public class SensorHealthController {
    private final SensorService sensors;
    private final SensorReadingService readings;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    public record Health(Long sensorId,String communicationStatus,Instant lastMeasuredAt,Long ageSeconds,
                         String policy,java.math.BigDecimal batteryPercent, String administrativeStatus,
                         java.time.LocalDate calibratedAt,String maintenanceNote,String calculatedHealth) { }
    @GetMapping("/api/sensors/{id}/health")
    public Health get(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id) {
        var sensor = sensors.get(jwt.getSubject(),id);
        var metadata = jdbc.queryForMap("SELECT calibrated_at, maintenance_note FROM sensors WHERE id=?",id);
        var date = (java.sql.Date) metadata.get("calibrated_at");
        var calibrated = date == null ? null : date.toLocalDate();
        String note = (String) metadata.get("maintenance_note");
        var page=readings.listBySensor(jwt.getSubject(),id,0,20);
        var health=SensorHealthCalculator.calculate(page.content().stream().map(r->new SensorHealthCalculator.Sample(
                r.measuredAt(),number(r.soilMoisturePercent()),number(r.temperatureCelsius()),number(r.airHumidityPercent())))
                .toList(),Instant.now(),Duration.ofHours(24),Duration.ofHours(48));
        if(page.content().isEmpty()) return new Health(id,"OFFLINE",null,null,"DEMO: stale=24h; offline=48h",null,
                sensor.status().name(),calibrated,note,health);
        var r=page.content().getFirst(); Instant measured=r.measuredAt();
        long age=Duration.between(measured,Instant.now()).getSeconds();
        return new Health(id,age<0 ? "INVALID_TIMESTAMP" : age>=172800 ? "OFFLINE" : age>=86400 ? "STALE" : "RECENT",
                measured,age,"DEMO: stale=24h; offline=48h; flat=6/50min; jump=5min",r.batteryPercent(),sensor.status().name(),calibrated,note,health);
    }
    private Double number(java.math.BigDecimal value) { return value==null ? null : value.doubleValue(); }
}
