package br.com.fiap.citizentech.sensorreading.controller;

import br.com.fiap.citizentech.sensorreading.dto.*;
import br.com.fiap.citizentech.sensorreading.service.SensorReadingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** Explicit demo-only ingestion; ownership and validation use the production service. */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name="app.demo.enabled", havingValue="true")
public class SimulatedReadingController {
    private final SensorReadingService readings;

    @io.swagger.v3.oas.annotations.Operation(summary="Receber leitura simulada (APP_DEMO_ENABLED=true)")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="201",description="Leitura SIMULATED persistida"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="400",description="Entrada inválida"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="401",description="JWT ausente ou inválido"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="404",description="Sensor ausente ou de outro produtor"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="409",description="Sensor inativo ou cultivo arquivado")})
    @PostMapping("/api/simulated-readings")
    @ResponseStatus(HttpStatus.CREATED)
    public ReadingResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateReadingRequest body) {
        return readings.simulate(jwt.getSubject(), body);
    }
}
