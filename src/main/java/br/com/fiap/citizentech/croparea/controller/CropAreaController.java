package br.com.fiap.citizentech.croparea.controller;

import br.com.fiap.citizentech.croparea.dto.CropAreaPageResponse;
import br.com.fiap.citizentech.croparea.dto.CropAreaRequest;
import br.com.fiap.citizentech.croparea.dto.CropAreaResponse;
import br.com.fiap.citizentech.croparea.service.CropAreaService;
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
public class CropAreaController {
    private final CropAreaService cropAreaService;

    @PostMapping("/crop-areas")
    @ResponseStatus(HttpStatus.CREATED)
    public CropAreaResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CropAreaRequest request) {
        return cropAreaService.create(jwt.getSubject(), request);
    }

    @GetMapping("/crop-areas")
    public CropAreaPageResponse list(@AuthenticationPrincipal Jwt jwt,
                                     @RequestParam(defaultValue = "0") @Min(0) int page,
                                     @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return cropAreaService.list(jwt.getSubject(), page, size);
    }

    @GetMapping("/farms/{farmId}/crop-areas")
    public CropAreaPageResponse listByFarm(@AuthenticationPrincipal Jwt jwt, @PathVariable Long farmId,
                                           @RequestParam(defaultValue = "0") @Min(0) int page,
                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return cropAreaService.listByFarm(jwt.getSubject(), farmId, page, size);
    }

    @GetMapping("/crop-areas/{id}")
    public CropAreaResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return cropAreaService.get(jwt.getSubject(), id);
    }

    @PutMapping("/crop-areas/{id}")
    public CropAreaResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                   @Valid @RequestBody CropAreaRequest request) {
        return cropAreaService.update(jwt.getSubject(), id, request);
    }

    @DeleteMapping("/crop-areas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        cropAreaService.delete(jwt.getSubject(), id);
    }
}
