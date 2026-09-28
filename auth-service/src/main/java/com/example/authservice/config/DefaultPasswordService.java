package com.example.authservice.config;

import com.example.authservice.repository.AuthUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DefaultPasswordService {

    private final AuthUserRepository authUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtConfig jwtConfig;
    private final RestClient userServiceClient;

    public DefaultPasswordService(
            AuthUserRepository authUserRepository,
            PasswordEncoder passwordEncoder,
            JwtConfig jwtConfig,
            @Value("${user-service.base-url}") String baseUrl) {
        this.authUserRepository = authUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtConfig = jwtConfig;
        this.userServiceClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwtConfig.generateServiceToken())
                .build();
    }

    public String defaultPasswordFor(String name) {
        return name == null || name.isBlank() ? "user123" : name.trim() + "123";
    }

    public boolean passwordMatches(String raw, String encoded) {
        return passwordEncoder.matches(raw, encoded);
    }

    public String encode(String raw) {
        return passwordEncoder.encode(raw);
    }

    public boolean emailExists(String email) {
        return authUserRepository.existsByEmail(email);
    }

    public RestClient userService() {
        return userServiceClient;
    }
}