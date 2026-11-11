package br.com.fiap.citizentech.water;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import jakarta.validation.constraints.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api")
public class RiskJobController {
    private final RiskJobService service;
    @PostMapping("/crop-areas/{id}/risk-jobs") @ResponseStatus(HttpStatus.ACCEPTED)
    public RiskJob create(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id) { return service.create(jwt.getSubject(),id); }
    @GetMapping("/risk-jobs/{id}") public RiskJob get(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id) { return service.get(jwt.getSubject(),id); }
    @GetMapping("/crop-areas/{id}/risk-jobs") public Page<RiskJob> list(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.list(jwt.getSubject(),id,page,size);
    }
    @GetMapping("/alerts") public Page<RiskJob> alerts(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.alerts(jwt.getSubject(),page,size);
    }
    @PatchMapping("/alerts/{id}/acknowledge") public RiskJob acknowledge(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id) { return service.transition(jwt.getSubject(),id,false); }
    @GetMapping("/alerts/{id}")
    public RiskJob alert(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.alert(jwt.getSubject(), id);
    }
    @GetMapping("/crop-areas/{id}/alerts")
    public Page<RiskJob> cropAlerts(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.cropAlerts(jwt.getSubject(), id, page, size);
    }
    @PatchMapping("/alerts/{id}/resolve") public RiskJob resolve(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id) { return service.transition(jwt.getSubject(),id,true); }
}
