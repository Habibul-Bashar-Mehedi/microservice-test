package com.example.authservice.service;

import com.example.authservice.config.DefaultPasswordService;
import com.example.authservice.config.JwtConfig;
import com.example.authservice.entity.AuthUser;
import com.example.authservice.repository.AuthUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthUserRepository authUserRepository;
    private final DefaultPasswordService defaultPasswordService;
    private final JwtConfig jwtConfig;

    public LoginResult login(String email, String password) {
        AuthUser authUser = authUserRepository.findByEmail(email).orElse(null);

        if (authUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        UserProfile profile = findExistingUser(email);
        if (profile != null && !profile.active()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is not activated yet. Please contact admin.");
        }

        if (!defaultPasswordService.passwordMatches(password, authUser.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        String token = jwtConfig.generateToken(authUser.getEmail(), authUser.getName(), authUser.getRole());
        return new LoginResult(token, "Bearer", authUser.getEmail(), authUser.getName(), authUser.getRole(), null);
    }

    public LoginResult register(String name, String email, String password) {
        if (defaultPasswordService.emailExists(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User with email " + email + " already exists");
        }

        registerInUserService(name, email);

        AuthUser authUser = AuthUser.builder()
                .name(name)
                .email(email)
                .password(defaultPasswordService.encode(password))
                .role("USER")
                .build();
        authUserRepository.save(authUser);

        return new LoginResult(null, null, email, name, "USER", null);
    }

    public AuthUser getUserByEmail(String email) {
        return authUserRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private UserProfile findExistingUser(String email) {
        RestClient client = defaultPasswordService.userService();

        try {
            return client.get()
                    .uri("/v1/users/email/{email}", email)
                    .retrieve()
                    .body(UserProfile.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void registerInUserService(String name, String email) {
        RestClient client = defaultPasswordService.userService();

        client.post()
                .uri("/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterProfileRequest(name, email))
                .retrieve()
                .toBodilessEntity();
    }

    public record LoginResult(
            String accessToken,
            String tokenType,
            String email,
            String name,
            String role,
            String defaultPassword) {
    }

    public record UserProfile(Long id, String name, String email, boolean active) {
    }

    public record RegisterProfileRequest(String name, String email) {
    }
}