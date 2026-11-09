package br.com.fiap.citizentech.analysis.controller;

import br.com.fiap.citizentech.analysis.dto.*;
import br.com.fiap.citizentech.analysis.service.AnalysisQueryService;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class AnalysisController {
    private final AnalysisQueryService service;
    @GetMapping("/analyses")
    public AnalysisPageResponse list(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") @Min(0) int page,
                                     @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.list(jwt.getSubject(),page,size);
    }
    @GetMapping("/analyses/{id}")
    public AnalysisResponse get(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) {
        return service.get(jwt.getSubject(),id);
    }
    @GetMapping("/sensor-readings/{readingId}/analysis")
    public AnalysisResponse byReading(@AuthenticationPrincipal Jwt jwt,@PathVariable long readingId) {
        return service.byReading(jwt.getSubject(),readingId);
    }
    @GetMapping("/crop-areas/{cropAreaId}/analyses")
    public AnalysisPageResponse byCrop(@AuthenticationPrincipal Jwt jwt,@PathVariable long cropAreaId,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.byCrop(jwt.getSubject(),cropAreaId,page,size);
    }
    @PostMapping("/sensor-readings/{readingId}/analysis/retry") @ResponseStatus(HttpStatus.ACCEPTED)
    public AnalysisResponse retry(@AuthenticationPrincipal Jwt jwt,@PathVariable long readingId) {
        return service.retry(jwt.getSubject(),readingId);
    }
}
