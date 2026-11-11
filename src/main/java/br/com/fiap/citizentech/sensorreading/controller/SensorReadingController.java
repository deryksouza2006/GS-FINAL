package br.com.fiap.citizentech.sensorreading.controller;

import br.com.fiap.citizentech.sensorreading.dto.*;
import br.com.fiap.citizentech.sensorreading.service.SensorReadingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SensorReadingController {
    private final SensorReadingService service;

    @PostMapping("/sensors/{sensorId}/device-key")
    public org.springframework.http.ResponseEntity<java.util.Map<String,String>> rotateKey(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long sensorId) {
        return org.springframework.http.ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(java.util.Map.of("deviceKey", service.rotateDeviceKey(jwt.getSubject(), sensorId)));
    }

    @PostMapping("/device-readings") @ResponseStatus(HttpStatus.CREATED)
    public ReadingResponse device(@RequestHeader(name="X-Device-Key", required=false) String key,
                                  @Valid @RequestBody CreateReadingRequest request) {
        return service.ingestDevice(key, request);
    }

    @PostMapping("/sensor-readings") @ResponseStatus(HttpStatus.CREATED)
    public ReadingResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateReadingRequest request) {
        return service.create(jwt.getSubject(), request);
    }
    @GetMapping("/sensor-readings")
    public ReadingPageResponse list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(jwt.getSubject(), page, size);
    }
    @GetMapping("/sensor-readings/{id}")
    public ReadingResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) { return service.get(jwt.getSubject(), id); }
    @DeleteMapping("/sensor-readings/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) { service.delete(jwt.getSubject(), id); }
    @GetMapping("/sensors/{sensorId}/readings")
    public ReadingPageResponse bySensor(@AuthenticationPrincipal Jwt jwt, @PathVariable Long sensorId,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.listBySensor(jwt.getSubject(), sensorId, page, size);
    }
    @GetMapping("/crop-areas/{cropAreaId}/readings")
    public ReadingPageResponse byCrop(@AuthenticationPrincipal Jwt jwt, @PathVariable Long cropAreaId,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.listByCropArea(jwt.getSubject(), cropAreaId, page, size);
    }
    @GetMapping("/crop-areas/{cropAreaId}/readings/latest")
    public ReadingResponse latest(@AuthenticationPrincipal Jwt jwt, @PathVariable Long cropAreaId) { return service.latest(jwt.getSubject(), cropAreaId); }
}
