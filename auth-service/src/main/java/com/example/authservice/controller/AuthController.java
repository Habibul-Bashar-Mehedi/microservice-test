package com.example.authservice.controller;

import com.example.authservice.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        AuthService.LoginResult result = authService.login(request.email(), request.password());
        return ResponseEntity.ok(toResponse(result));
    }

    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@RequestBody @Valid RegisterRequest request) {
        AuthService.LoginResult result = authService.register(request.name(), request.email(), request.password());
        return ResponseEntity.ok(toResponse(result));
    }

    private LoginResponse toResponse(AuthService.LoginResult result) {
        return new LoginResponse(
                result.accessToken(),
                result.tokenType(),
                result.email(),
                result.name(),
                result.role(),
                result.defaultPassword());
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, max = 100) String password) {
    }

    public record RegisterRequest(
            @NotBlank String name,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, max = 100) String password) {
    }

    public record LoginResponse(
            String accessToken,
            String tokenType,
            String email,
            String name,
            String role,
            String defaultPassword) {
    }
}