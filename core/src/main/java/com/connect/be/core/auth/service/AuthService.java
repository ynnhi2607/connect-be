package com.connect.be.core.auth.service;

import com.connect.be.core.auth.dto.AuthResponse;
import com.connect.be.core.auth.dto.LoginRequest;
import com.connect.be.core.auth.dto.RegisterRequest;
import com.connect.be.core.auth.security.JwtService;
import com.connect.be.core.user.dto.UserResponse;
import com.connect.be.core.user.mapper.UserMapper;
import com.connect.be.core.user.model.AppUser;
import com.connect.be.core.user.model.AuthProvider;
import com.connect.be.core.user.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email da duoc su dung");
        }

        AppUser user = userRepository.save(AppUser.builder()
                .name(request.name().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .authProvider(AuthProvider.LOCAL)
                .build());

        return authResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        AppUser user = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(this::badCredentials);

        if (!canLoginWithPassword(user) || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw badCredentials();
        }

        return authResponse(user);
    }

    public UserResponse currentUser(String email) {
        return userRepository.findByEmail(email)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token khong hop le"));
    }

    private AuthResponse authResponse(AppUser user) {
        JwtService.TokenIssue token = jwtService.issueToken(user);
        return new AuthResponse(token.token(), token.expiresAt(), userMapper.toResponse(user));
    }

    private ResponseStatusException badCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email hoac mat khau khong dung");
    }

    private boolean canLoginWithPassword(AppUser user) {
        return user.getAuthProvider() == AuthProvider.LOCAL && user.getPasswordHash() != null;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
