package br.com.fiap.citizentech.activity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import java.time.Instant;

@RestController @RequiredArgsConstructor @RequestMapping("/api/crop-areas/{cropAreaId}/activities")
public class ActivityController {
    private final ActivityService service;
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ActivityResponse create(@AuthenticationPrincipal Jwt jwt,@PathVariable Long cropAreaId,@Valid @RequestBody ActivityRequest body) {
        return service.create(jwt.getSubject(),cropAreaId,body);
    }
    @GetMapping public Page<ActivityResponse> list(@AuthenticationPrincipal Jwt jwt,@PathVariable Long cropAreaId,
            @RequestParam(required=false) FieldActivity.Type type,@RequestParam(required=false) Instant from,
            @RequestParam(required=false) Instant to,@RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.list(jwt.getSubject(),cropAreaId,type,from,to,page,size);
    }
    @GetMapping("/{activityId}") public ActivityResponse get(@AuthenticationPrincipal Jwt jwt,@PathVariable Long cropAreaId,@PathVariable Long activityId) {
        return service.get(jwt.getSubject(),cropAreaId,activityId);
    }
    @PutMapping("/{activityId}") public ActivityResponse update(@AuthenticationPrincipal Jwt jwt,@PathVariable Long cropAreaId,@PathVariable Long activityId,@Valid @RequestBody ActivityRequest body) {
        return service.update(jwt.getSubject(),cropAreaId,activityId,body);
    }
    @DeleteMapping("/{activityId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt,@PathVariable Long cropAreaId,@PathVariable Long activityId) {
        service.delete(jwt.getSubject(),cropAreaId,activityId);
    }
}
