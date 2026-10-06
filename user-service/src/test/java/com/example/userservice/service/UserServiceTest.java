package com.example.userservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.userservice.entity.User;
import com.example.userservice.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, RestClient.builder(), null);
    }

    private User user(Long id, String email, String role, boolean active) {
        return User.builder().id(id).name("Alice").email(email).role(role).active(active).build();
    }

    @Test
    void createUser_defaultsToInactiveAndUserRole() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User created = userService.createUser(User.builder().name("Alice").email("alice@example.com").build());

        assertThat(created.getActive()).isFalse();
        assertThat(created.getRole()).isEqualTo("USER");
    }

    @Test
    void createUser_acceptsManagerRole() {
        when(userRepository.existsByEmail("m@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User created = userService.createUser(
                User.builder().name("M").email("m@example.com").role("manager").build());

        assertThat(created.getRole()).isEqualTo("MANAGER");
    }

    @Test
    void createUser_conflictWhenEmailExists() {
        when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(
                User.builder().name("D").email("dup@example.com").build()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_rejectsInvalidRole() {
        when(userRepository.existsByEmail("x@example.com")).thenReturn(false);

        assertThatThrownBy(() -> userService.createUser(
                User.builder().name("X").email("x@example.com").role("SUPERUSER").build()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void register_createsNewUser() {
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User registered = userService.register(
                User.builder().name("New").email("new@example.com").role("MAINTAINER").build());

        assertThat(registered.getRole()).isEqualTo("MAINTAINER");
    }

    @Test
    void register_updatesRoleForExistingUser() {
        User existing = user(1L, "old@example.com", "USER", true);
        when(userRepository.findByEmail("old@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User registered = userService.register(
                User.builder().name("Old").email("old@example.com").role("MANAGER").build());

        assertThat(registered.getRole()).isEqualTo("MANAGER");
        assertThat(registered.getId()).isEqualTo(1L);
    }

    @Test
    void getUserByEmail_returnsUser() {
        User existing = user(1L, "a@example.com", "USER", true);
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(existing));

        assertThat(userService.getUserByEmail("a@example.com")).isSameAs(existing);
    }

    @Test
    void getUserByEmail_notFound() {
        when(userRepository.findByEmail("none@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserByEmail("none@example.com"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void setActive_togglesActive() {
        User existing = user(1L, "a@example.com", "USER", false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User updated = userService.setActive(1L, true);

        assertThat(updated.getActive()).isTrue();
    }

    @Test
    void setActive_notFound() {
        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.setActive(9L, true))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void setRole_updatesRoleWithoutSyncWhenNoAuthHeader() {
        User existing = user(1L, "a@example.com", "USER", true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User updated = userService.setRole(1L, "maintainer", null);

        assertThat(updated.getRole()).isEqualTo("MAINTAINER");
    }

    @Test
    void setRole_rejectsInvalidRole() {
        assertThatThrownBy(() -> userService.setRole(1L, "ROOT", null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void setRole_notFound() {
        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.setRole(9L, "ADMIN", null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getAllUsers_delegatesToRepository() {
        List<User> users = List.of(user(1L, "a@example.com", "USER", true));
        when(userRepository.findAllByOrderByIdDesc()).thenReturn(users);

        assertThat(userService.getAllUsers()).isEqualTo(users);
    }
}