package br.com.fiap.citizentech.auth.dto;

import br.com.fiap.citizentech.user.domain.User;
import br.com.fiap.citizentech.user.domain.UserRole;

public record UserResponse(Long id, String name, String email, UserRole role) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
