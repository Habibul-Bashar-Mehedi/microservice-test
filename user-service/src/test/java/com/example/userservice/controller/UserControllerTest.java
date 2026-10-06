package com.example.userservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.userservice.entity.User;
import com.example.userservice.service.UserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    private UserController controller;

    @BeforeEach
    void setUp() {
        controller = new UserController(userService);
    }

    private User user(Long id, String email) {
        return User.builder().id(id).name("Alice").email(email).role("USER").active(true).build();
    }

    @Test
    void createUserV1_delegates() {
        when(userService.createUser(any(User.class))).thenReturn(user(1L, "a@x.com"));
        assertThat(controller.createUserV1(user(null, "a@x.com"))).isNotNull();
    }

    @Test
    void getAllUsersV1_delegates() {
        when(userService.getAllUsers()).thenReturn(List.of(user(1L, "a@x.com")));
        assertThat(controller.getAllUsersV1()).hasSize(1);
    }

    @Test
    void getUserByIdV1_delegates() {
        when(userService.getUserById(1L)).thenReturn(user(1L, "a@x.com"));
        assertThat(controller.getUserByIdV1(1L)).isNotNull();
    }

    @Test
    void getUserByEmailV1_delegates() {
        when(userService.getUserByEmail("a@x.com")).thenReturn(user(1L, "a@x.com"));
        assertThat(controller.getUserByEmailV1("a@x.com")).isNotNull();
    }

    @Test
    void registerV1_allowsOwnProfile() {
        when(userService.register(any(User.class))).thenReturn(user(1L, "a@x.com"));
        Authentication auth = new UsernamePasswordAuthenticationToken("a@x.com", null);

        assertThat(controller.registerV1(user(null, "a@x.com"), auth)).isNotNull();
    }

    @Test
    void registerV1_rejectsOtherProfile() {
        Authentication auth = new UsernamePasswordAuthenticationToken("other@x.com", null);

        assertThatThrownBy(() -> controller.registerV1(user(null, "a@x.com"), auth))
                .isInstanceOf(ResponseStatusException.class);
        verify(userService, never()).register(any());
    }

    @Test
    void setActiveV1_delegates() {
        when(userService.setActive(1L, false)).thenReturn(user(1L, "a@x.com"));
        assertThat(controller.setActiveV1(1L, new UserController.ActiveStatusRequest(false))).isNotNull();
    }

    @Test
    void changeRoleV1_passesAuthorizationHeader() {
        when(userService.setRole(eq(1L), eq("MANAGER"), eq("Bearer t"))).thenReturn(user(1L, "a@x.com"));

        controller.changeRoleV1(1L, "Bearer t", new UserController.ChangeRoleRequest("MANAGER"));

        verify(userService).setRole(1L, "MANAGER", "Bearer t");
    }
}