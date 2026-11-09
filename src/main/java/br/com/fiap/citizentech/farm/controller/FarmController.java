package br.com.fiap.citizentech.farm.controller;

import br.com.fiap.citizentech.farm.dto.FarmPageResponse;
import br.com.fiap.citizentech.farm.dto.FarmRequest;
import br.com.fiap.citizentech.farm.dto.FarmResponse;
import br.com.fiap.citizentech.farm.service.FarmService;
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
@RequestMapping("/api/farms")
@RequiredArgsConstructor
public class FarmController {
    private final FarmService farmService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FarmResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody FarmRequest request) {
        return farmService.create(jwt.getSubject(), request);
    }

    @GetMapping
    public FarmPageResponse list(@AuthenticationPrincipal Jwt jwt,
                                 @RequestParam(defaultValue = "0") @Min(0) int page,
                                 @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return farmService.list(jwt.getSubject(), page, size);
    }

    @GetMapping("/{id}")
    public FarmResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return farmService.get(jwt.getSubject(), id);
    }

    @PutMapping("/{id}")
    public FarmResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                               @Valid @RequestBody FarmRequest request) {
        return farmService.update(jwt.getSubject(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        farmService.delete(jwt.getSubject(), id);
    }
}
