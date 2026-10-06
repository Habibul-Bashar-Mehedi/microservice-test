package com.example.authservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.authservice.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authService);
    }

    @Test
    void google_mapsLoginResultToResponse() {
        when(authService.googleLogin("tok")).thenReturn(new AuthService.LoginResult(
                "jwt", "Bearer", "a@x.com", "Alice", "MANAGER", null));

        var response = controller.google(new AuthController.GoogleRequest("tok"));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().role()).isEqualTo("MANAGER");
        assertThat(response.getBody().email()).isEqualTo("a@x.com");
    }

    @Test
    void changeRole_delegates() {
        controller.changeRole("a@x.com", new AuthController.ChangeRoleRequest("MAINTAINER"));
        verify(authService).changeRole("a@x.com", "MAINTAINER");
    }
}