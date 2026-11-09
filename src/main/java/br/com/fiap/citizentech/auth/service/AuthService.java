package br.com.fiap.citizentech.auth.service;

import br.com.fiap.citizentech.auth.dto.AuthResponse;
import br.com.fiap.citizentech.auth.dto.LoginRequest;
import br.com.fiap.citizentech.auth.dto.RegisterRequest;
import br.com.fiap.citizentech.auth.dto.UserResponse;
import br.com.fiap.citizentech.shared.exception.BusinessException;
import br.com.fiap.citizentech.user.domain.User;
import br.com.fiap.citizentech.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw duplicateEmail();
        }
        User user = new User(request.name(), request.email(), passwordEncoder.encode(request.password()));
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // A restrição no banco também protege cadastros concorrentes.
            if (ex.getCause() instanceof ConstraintViolationException constraint
                    && ("ux_users_email_lower".equals(constraint.getConstraintName())
                    || "users_email_key".equals(constraint.getConstraintName()))) {
                throw duplicateEmail();
            }
            throw ex;
        }
        return jwtService.issue(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
        return jwtService.issue((User) authentication.getPrincipal());
    }

    @Transactional(readOnly = true)
    public UserResponse me(String email) {
        return users.findByEmailIgnoreCase(email).map(UserResponse::from)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
    }

    private BusinessException duplicateEmail() {
        return new BusinessException(HttpStatus.CONFLICT, "E-mail já cadastrado");
    }
}
