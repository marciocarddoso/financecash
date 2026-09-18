package br.com.finkeeper.application.service;

import br.com.finkeeper.application.dto.AuthResponse;
import br.com.finkeeper.application.dto.LoginRequest;
import br.com.finkeeper.application.dto.RegisterRequest;
import br.com.finkeeper.domain.model.AppUser;
import br.com.finkeeper.domain.repository.AppUserRepository;
import br.com.finkeeper.exception.BusinessException;
import br.com.finkeeper.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder,
                        AuthenticationManager authenticationManager, JwtService jwtService) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (appUserRepository.existsByEmail(request.email())) {
            throw new BusinessException("Já existe um usuário cadastrado com este e-mail.");
        }

        AppUser user = AppUser.builder()
                .name(request.name())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .active(true)
                .build();
        appUserRepository.save(user);

        String token = jwtService.generateToken(user.getEmail());
        return AuthResponse.bearer(token, user.getName(), user.getEmail());
    }

    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (BadCredentialsException ex) {
            throw new BusinessException("E-mail ou senha inválidos.");
        }

        AppUser user = appUserRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException("E-mail ou senha inválidos."));

        String token = jwtService.generateToken(user.getEmail());
        return AuthResponse.bearer(token, user.getName(), user.getEmail());
    }
}
