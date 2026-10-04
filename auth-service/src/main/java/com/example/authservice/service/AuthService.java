package com.example.authservice.service;

import com.example.authservice.config.JwtService;
import com.example.authservice.entity.AuthUser;
import com.example.authservice.repository.AuthUserRepository;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final AuthUserRepository authUserRepository;
    private final JwtDecoder googleJwtDecoder;
    private final JwtService jwtService;
    private final RestClient.Builder restClientBuilder;
    private final String userServiceBaseUrl;

    public AuthService(
            AuthUserRepository authUserRepository,
            @Qualifier("googleJwtDecoder") JwtDecoder googleJwtDecoder,
            JwtService jwtService,
            RestClient.Builder restClientBuilder,
            @Value("${user-service.base-url}") String userServiceBaseUrl) {
        this.authUserRepository = authUserRepository;
        this.googleJwtDecoder = googleJwtDecoder;
        this.jwtService = jwtService;
        this.restClientBuilder = restClientBuilder;
        this.userServiceBaseUrl = userServiceBaseUrl;
    }

    public LoginResult googleLogin(String idToken) {
        Jwt jwt = verifyGoogleToken(idToken);
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google account has no email");
        }

        String role = resolveRole(name, email);
        String accessToken = jwtService.generateToken(email, name, role);
        UserProfile profile = registerInUserService(name, email, role, accessToken);

        if (Boolean.FALSE.equals(profile.active())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Account is inactive. Please contact an administrator."
            );
        }

        return new LoginResult(accessToken, "Bearer", email, name, role, null);
    }

    private String resolveRole(String name, String email) {
        AuthUser existing = authUserRepository.findByEmail(email).orElse(null);
        if (existing != null) {
            return existing.getRole();
        }
        authUserRepository.save(AuthUser.builder()
                .name(name)
                .email(email)
                .role("USER")
                .build());
        return "USER";
    }

    private Jwt verifyGoogleToken(String idToken) {
        try {
            return googleJwtDecoder.decode(idToken);
        } catch (JwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google token");
        }
    }

    private UserProfile registerInUserService(String name, String email, String role, String accessToken) {
        RestClient client = restClientBuilder
                .baseUrl(userServiceBaseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .build();

        try {
            return client.post()
                    .uri("/v1/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new RegisterProfileRequest(name, email, true, role))
                    .retrieve()
                    .body(UserProfile.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() != HttpStatus.CONFLICT.value()) {
                throw e;
            }
        }

        return fetchUserProfile(email, client);
    }

    private UserProfile fetchUserProfile(String email, RestClient client) {
        try {
            return client.get()
                    .uri("/v1/users/email/{email}", email)
                    .retrieve()
                    .body(UserProfile.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Profile not found in user-service"
                );
            }
            throw e;
        }
    }

    public void changeRole(String email, String role) {
        String normalized = role == null ? null : role.trim().toUpperCase(Locale.ROOT);
        if (!"ADMIN".equals(normalized) && !"USER".equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role must be ADMIN or USER");
        }

        AuthUser authUser = authUserRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found with email: " + email
                ));

        authUser.setRole(normalized);
        authUserRepository.save(authUser);
    }

    public record LoginResult(
            String accessToken,
            String tokenType,
            String email,
            String name,
            String role,
            String defaultPassword) {
    }

    public record RegisterProfileRequest(String name, String email, boolean active, String role) {
    }

    public record UserProfile(String email, boolean active) {
    }
}