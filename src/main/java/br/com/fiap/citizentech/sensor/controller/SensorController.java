package br.com.fiap.citizentech.sensor.controller;

import br.com.fiap.citizentech.sensor.dto.CreateSensorRequest;
import br.com.fiap.citizentech.sensor.dto.SensorPageResponse;
import br.com.fiap.citizentech.sensor.dto.SensorResponse;
import br.com.fiap.citizentech.sensor.dto.UpdateSensorRequest;
import br.com.fiap.citizentech.sensor.service.SensorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SensorController {
    private final SensorService sensorService;

    @PostMapping("/sensors")
    @ResponseStatus(HttpStatus.CREATED)
    public SensorResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateSensorRequest request) {
        return sensorService.create(jwt.getSubject(), request);
    }

    @GetMapping("/sensors")
    public SensorPageResponse list(@AuthenticationPrincipal Jwt jwt,
                                   @RequestParam(defaultValue = "0") @Min(0) int page,
                                   @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return sensorService.list(jwt.getSubject(), page, size);
    }

    @GetMapping("/crop-areas/{cropAreaId}/sensors")
    public SensorPageResponse listByCropArea(@AuthenticationPrincipal Jwt jwt, @PathVariable Long cropAreaId,
                                             @RequestParam(defaultValue = "0") @Min(0) int page,
                                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return sensorService.listByCropArea(jwt.getSubject(), cropAreaId, page, size);
    }

    @GetMapping("/sensors/{id}")
    public SensorResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return sensorService.get(jwt.getSubject(), id);
    }

    @PutMapping("/sensors/{id}")
    public SensorResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                 @Valid @RequestBody UpdateSensorRequest request) {
        return sensorService.update(jwt.getSubject(), id, request);
    }

    @DeleteMapping("/sensors/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        sensorService.delete(jwt.getSubject(), id);
    }
}
