package br.com.fiap.citizentech.auth.dto;

import java.time.Instant;

public record AuthResponse(String token, String tokenType, Instant expiresAt, UserResponse user) {
}
