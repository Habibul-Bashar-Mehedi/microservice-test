package com.example.authservice.controller;

import com.example.authservice.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/google")
    public ResponseEntity<LoginResponse> google(@RequestBody @Valid GoogleRequest request) {
        AuthService.LoginResult result = authService.googleLogin(request.idToken());
        return ResponseEntity.ok(toResponse(result));
    }

    @PatchMapping("/users/{email}/role")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeRole(@PathVariable String email, @RequestBody @Valid ChangeRoleRequest request) {
        authService.changeRole(email, request.role());
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

    public record GoogleRequest(@NotBlank String idToken) {
    }

    public record ChangeRoleRequest(@NotBlank String role) {
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