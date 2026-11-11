package br.com.fiap.citizentech.water;
import br.com.fiap.citizentech.croparea.service.CropAreaService;
import br.com.fiap.citizentech.sensorreading.service.SensorReadingService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import java.time.Instant;
import java.util.Map;
@RestController @RequiredArgsConstructor
public class DashboardController {
    private final CropAreaService crops;
    private final SensorReadingService readings;
    private final RiskJobService jobs;
    private final br.com.fiap.citizentech.farm.service.FarmService farms;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final br.com.fiap.citizentech.user.repository.UserRepository users;
    @GetMapping("/api/dashboard")
    public Map<String,Object> get(@AuthenticationPrincipal Jwt jwt) {
        String email=jwt.getSubject();
        var cropPage = crops.list(email,0,20);
        var owner = users.findByEmailIgnoreCase(email).orElseThrow().getId();
        var summaries = jdbc.queryForList("""
                SELECT c.id AS "cropAreaId", r.id AS "latestReadingId", r.measured_at AS "measuredAt",
                       r.source, j.id AS "latestJobId", j.result->>'riskLevel' AS "riskLevel",
                       (j.result->>'waterAvailabilityPercent')::double precision AS "waterAvailabilityPercent",
                       (j.result->>'vpdKpa')::double precision AS "vpdKpa",
                       (j.result->>'moistureChangePerHour')::double precision AS "moistureChangePerHour"
                FROM crop_areas c JOIN farms f ON f.id=c.farm_id
                LEFT JOIN LATERAL (SELECT sr.id,sr.measured_at,sr.source FROM sensor_readings sr
                    JOIN sensors s ON s.id=sr.sensor_id WHERE s.crop_area_id=c.id
                    ORDER BY sr.measured_at DESC,sr.id DESC LIMIT 1) r ON TRUE
                LEFT JOIN LATERAL (SELECT id,result FROM risk_jobs
                    WHERE crop_area_id=c.id AND owner_id=f.owner_user_id AND result IS NOT NULL
                    ORDER BY id DESC LIMIT 1) j ON TRUE
                WHERE f.owner_user_id=? AND c.status<>'ARCHIVED' ORDER BY c.id LIMIT 20
                """, owner);
        var recentActivities = jdbc.queryForList("""
                SELECT a.id,a.crop_area_id AS "cropAreaId",a.type,a.performed_at AS "performedAt",a.description
                FROM field_activities a JOIN crop_areas c ON c.id=a.crop_area_id JOIN farms f ON f.id=c.farm_id
                WHERE f.owner_user_id=? ORDER BY a.performed_at DESC,a.id DESC LIMIT 20
                """, owner);
        var actionableJobs = jdbc.queryForList("""
                SELECT id,crop_area_id AS "cropAreaId",status,created_at AS "createdAt",error_message AS "errorMessage"
                FROM risk_jobs WHERE owner_id=? AND status IN ('PENDING','QUEUED','PROCESSING','FAILED')
                ORDER BY id DESC LIMIT 20
                """, owner);
        var sensorIssues = jdbc.queryForList("""
                SELECT s.id,s.crop_area_id AS "cropAreaId",s.name,s.status,latest.measured_at AS "lastMeasuredAt"
                FROM sensors s JOIN crop_areas c ON c.id=s.crop_area_id JOIN farms f ON f.id=c.farm_id
                LEFT JOIN LATERAL (SELECT measured_at FROM sensor_readings r WHERE r.sensor_id=s.id
                    ORDER BY measured_at DESC,id DESC LIMIT 1) latest ON TRUE
                WHERE f.owner_user_id=? AND c.status<>'ARCHIVED' AND
                    (s.status<>'ACTIVE' OR latest.measured_at IS NULL OR latest.measured_at<CURRENT_TIMESTAMP-INTERVAL '24 hours')
                ORDER BY s.id LIMIT 20
                """, owner);
        return Map.of("updatedAt",Instant.now(),"cropAreas",cropPage,"farms",farms.list(email,0,20),
                "recentReadings",readings.list(email,0,20),"alerts",jobs.openAlerts(email),
                "cropSummaries",summaries,"recentActivities",recentActivities,
                "actionableJobs",actionableJobs,"sensorIssues",sensorIssues);
    }
}
