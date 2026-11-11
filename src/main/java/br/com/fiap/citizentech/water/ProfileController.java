package br.com.fiap.citizentech.water;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
@RestController @RequiredArgsConstructor @RequestMapping("/api/crop-areas/{cropAreaId}/agronomic-profile")
public class ProfileController {
    private final ProfileService service;
    @GetMapping public ProfileService.Response get(@AuthenticationPrincipal Jwt jwt,@PathVariable Long cropAreaId) {
        return service.get(jwt.getSubject(),cropAreaId);
    }
    @PutMapping public ProfileService.Response put(@AuthenticationPrincipal Jwt jwt,@PathVariable Long cropAreaId,@Valid @RequestBody ProfileRequest body) {
        return service.put(jwt.getSubject(),cropAreaId,body);
    }
}
