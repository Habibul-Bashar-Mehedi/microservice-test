package com.example.authservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.authservice.config.JwtService;
import com.example.authservice.entity.AuthUser;
import com.example.authservice.repository.AuthUserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthUserRepository authUserRepository;
    @Mock
    private JwtDecoder googleJwtDecoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    @Mock
    private CircuitBreaker circuitBreaker;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                authUserRepository, googleJwtDecoder, jwtService, RestClient.builder(),
                circuitBreakerFactory, "http://localhost:8081");
    }

    private AuthUser authUser(String role) {
        return AuthUser.builder().id(1L).name("Alice").email("alice@example.com").role(role).build();
    }

    private Jwt jwt(Map<String, Object> claims) {
        return new Jwt("id-token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "none"), claims);
    }

    // ---- changeRole ----

    @Test
    void changeRole_normalizesToUpperCaseAndSaves() {
        AuthUser user = authUser("USER");
        when(authUserRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        authService.changeRole("alice@example.com", "manager");

        assertThat(user.getRole()).isEqualTo("MANAGER");
        verify(authUserRepository).save(user);
    }

    @Test
    void changeRole_acceptsAllSupportedRoles() {
        for (String role : List.of("USER", "ADMIN", "MANAGER", "MAINTAINER",
                "PRODUCT_SPECIALIST", "SALESMAN")) {
            AuthUser user = authUser("USER");
            when(authUserRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

            authService.changeRole("alice@example.com", role.toLowerCase());

            assertThat(user.getRole()).isEqualTo(role);
        }
    }

    @Test
    void changeRole_rejectsUnsupportedRole() {
        assertThatThrownBy(() -> authService.changeRole("alice@example.com", "SUPERUSER"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(authUserRepository, never()).save(any());
    }

    @Test
    void changeRole_rejectsBlankRole() {
        assertThatThrownBy(() -> authService.changeRole("alice@example.com", "   "))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void changeRole_throwsNotFoundWhenUserMissing() {
        when(authUserRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.changeRole("ghost@example.com", "ADMIN"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ---- google login ----

    @Test
    void googleLogin_newUserGetsUserRoleAndToken() {
        when(googleJwtDecoder.decode("id")).thenReturn(jwt(Map.of("email", "alice@example.com", "name", "Alice")));
        when(authUserRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(jwtService.generateToken("alice@example.com", "Alice", "USER")).thenReturn("access-token");
        when(circuitBreakerFactory.create("userService")).thenReturn(circuitBreaker);
        doReturn(new AuthService.UserProfile("alice@example.com", true))
                .when(circuitBreaker).run(any(), any());

        AuthService.LoginResult result = authService.googleLogin("id");

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.role()).isEqualTo("USER");
        verify(authUserRepository).save(any(AuthUser.class));
    }

    @Test
    void googleLogin_existingUserKeepsStoredRole() {
        when(googleJwtDecoder.decode("id")).thenReturn(jwt(Map.of("email", "alice@example.com", "name", "Alice")));
        when(authUserRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(authUser("ADMIN")));
        when(jwtService.generateToken("alice@example.com", "Alice", "ADMIN")).thenReturn("access-token");
        when(circuitBreakerFactory.create("userService")).thenReturn(circuitBreaker);
        doReturn(new AuthService.UserProfile("alice@example.com", true))
                .when(circuitBreaker).run(any(), any());

        AuthService.LoginResult result = authService.googleLogin("id");

        assertThat(result.role()).isEqualTo("ADMIN");
        verify(authUserRepository, never()).save(any());
    }

    @Test
    void googleLogin_inactiveProfileIsForbidden() {
        when(googleJwtDecoder.decode("id")).thenReturn(jwt(Map.of("email", "alice@example.com", "name", "Alice")));
        when(authUserRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(authUser("USER")));
        when(jwtService.generateToken(any(), any(), any())).thenReturn("t");
        when(circuitBreakerFactory.create("userService")).thenReturn(circuitBreaker);
        doReturn(new AuthService.UserProfile("alice@example.com", false))
                .when(circuitBreaker).run(any(), any());

        assertThatThrownBy(() -> authService.googleLogin("id"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void googleLogin_rejectsInvalidToken() {
        when(googleJwtDecoder.decode("bad")).thenThrow(new JwtException("invalid"));

        assertThatThrownBy(() -> authService.googleLogin("bad"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void googleLogin_rejectsTokenWithoutEmail() {
        when(googleJwtDecoder.decode("id")).thenReturn(jwt(Map.of("name", "Alice")));

        assertThatThrownBy(() -> authService.googleLogin("id"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}