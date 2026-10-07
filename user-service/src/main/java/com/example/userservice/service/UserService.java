package com.example.userservice.service;

import com.example.userservice.entity.User;
import com.example.userservice.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Set<String> ALLOWED_ROLES =
            Set.of("USER", "ADMIN", "MANAGER", "MAINTAINER", "PRODUCT_SPECIALIST", "SALESMAN");

    private final UserRepository userRepository;
    private final RestClient.Builder restClientBuilder;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    @Value("${auth-service.base-url}")
    private String authServiceBaseUrl;

    @Transactional
    @CacheEvict(value = {"users", "userByEmail"}, allEntries = true)
    public User createUser(User user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "User with email " + user.getEmail() + " already exists"
            );
        }

        user.setActive(false);
        user.setRole(normalizeRole(user.getRole()));
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    @Cacheable("users")
    public List<User> getAllUsers() {
        return userRepository.findAllByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    @Cacheable("userById")
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
    }

    @Transactional(readOnly = true)
    @Cacheable("userByEmail")
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found with email: " + email
                ));
    }

    @Transactional
    @CacheEvict(value = {"users", "userById", "userByEmail"}, allEntries = true)
    public User register(User user) {
        User existing = userRepository.findByEmail(user.getEmail()).orElse(null);
        if (existing != null) {
            existing.setRole(normalizeRole(user.getRole()));
            return userRepository.save(existing);
        }

        user.setRole(normalizeRole(user.getRole()));
        return userRepository.save(user);
    }

    @Transactional
    @CacheEvict(value = {"users", "userById", "userByEmail"}, allEntries = true)
    public User setActive(Long id, boolean active) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found with id: " + id
                ));

        user.setActive(active);
        return userRepository.save(user);
    }

    @Transactional
    @CacheEvict(value = {"users", "userById", "userByEmail"}, allEntries = true)
    public User setRole(Long id, String role, String authHeader) {
        String normalized = normalizeRole(role);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found with id: " + id
                ));

        user.setRole(normalized);
        User saved = userRepository.save(user);
        syncRoleWithAuthService(saved.getEmail(), normalized, authHeader);
        return saved;
    }

    private void syncRoleWithAuthService(String email, String role, String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            return;
        }

        circuitBreakerFactory.create("authService").run(
                () -> {
                    doSyncRoleWithAuthService(email, role, authHeader);
                    return null;
                },
                throwable -> {
                    if (throwable instanceof ResponseStatusException responseStatusException) {
                        throw responseStatusException;
                    }
                    throw new ResponseStatusException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "auth-service is unavailable: " + throwable.getMessage(),
                            throwable
                    );
                });
    }

    private void doSyncRoleWithAuthService(String email, String role, String authHeader) {
        RestClient client = restClientBuilder
                .baseUrl(authServiceBaseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .build();

        try {
            client.patch()
                    .uri("/auth/users/{email}/role", email)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("role", role))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw new ResponseStatusException(
                    e.getStatusCode(),
                    "Role updated locally but failed to sync with auth-service: "
                            + e.getResponseBodyAsString()
            );
        }
    }

    private String normalizeRole(String role) {
        String normalized = role == null || role.isBlank() ? null : role.trim().toUpperCase(Locale.ROOT);
        if (normalized == null) {
            return "USER";
        }
        if (!ALLOWED_ROLES.contains(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Role must be one of " + ALLOWED_ROLES
            );
        }
        return normalized;
    }
}