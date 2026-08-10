/**
 * Auth controller for registration, login, and token refresh.
 * All endpoints log audit events for security compliance.
 * Refresh endpoint enables long-lived sessions without re-authentication.
 */
package com.astrastore.auth.controller;

import com.astrastore.auth.dto.AuthResponse;
import com.astrastore.auth.dto.LoginRequest;
import com.astrastore.auth.dto.RefreshTokenRequest;
import com.astrastore.auth.dto.RegisterRequest;
import com.astrastore.auth.entity.AuditAction;
import com.astrastore.auth.entity.User;
import com.astrastore.auth.exception.AuthException;
import com.astrastore.auth.repository.UserRepository;
import com.astrastore.auth.security.JwtService;
import com.astrastore.auth.service.AuditLogService;
import com.astrastore.auth.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final AuditLogService auditLogService;

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {

        log.info("Registration attempt for email: {}", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            auditLogService.logEvent(null, AuditAction.REGISTER_FAILED,
                    httpRequest, false, "Email already registered");
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Email is already registered"));
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .roles(java.util.Set.of("USER"))
                .enabled(true)
                .build();

        User saved = userRepository.save(user);
        auditLogService.logEvent(saved.getId(), AuditAction.REGISTER_SUCCESS,
                httpRequest, true, null);
        log.info("User registered successfully: {}", request.getEmail());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Map.of("message", "User registered successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        log.info("Login attempt for email: {}", request.getEmail());

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );
        } catch (BadCredentialsException e) {
            auditLogService.logEvent(null, AuditAction.LOGIN_FAILED,
                    httpRequest, false, "Bad credentials");
            log.warn("Failed login attempt for email: {}", request.getEmail());
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid email or password"));
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow();

        String accessToken = jwtService.generateAccessToken(user);
        RefreshTokenService.CreatedRefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());

        auditLogService.logEvent(user.getId(), AuditAction.LOGIN_SUCCESS,
                httpRequest, true, null);
        log.info("User logged in successfully: {}", request.getEmail());

        AuthResponse response = AuthResponse.builder()
                .token(accessToken)
                .type("Bearer")
                .refreshToken(refreshToken.rawToken())
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .roles(user.getRoles())
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {

        log.info("Refresh token request received");

        User user = refreshTokenService.validateRefreshToken(request.getRefreshToken())
                .orElseThrow(() -> {
                    auditLogService.logEvent(null, AuditAction.REFRESH_TOKEN_FAILED,
                            httpRequest, false, "Invalid or expired refresh token");
                    return new AuthException("Invalid refresh token");
                });

        RefreshTokenService.CreatedRefreshToken newRefresh = refreshTokenService.rotateRefreshToken(request.getRefreshToken());
        String accessToken = jwtService.generateAccessToken(user);

        auditLogService.logEvent(user.getId(), AuditAction.REFRESH_TOKEN_SUCCESS,
                httpRequest, true, null);

        AuthResponse response = AuthResponse.builder()
                .token(accessToken)
                .type("Bearer")
                .refreshToken(newRefresh.rawToken())
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .roles(user.getRoles())
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {

        log.info("Logout attempt received");
        try {
            refreshTokenService.revokeToken(request.getRefreshToken());
            auditLogService.logEvent(null, AuditAction.LOGOUT, httpRequest, true, null);
            log.info("Logout successful");
            return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
        } catch (Exception e) {
            log.error("Logout error: {}", e.getMessage(), e);
            throw e;
        }
    }
}
