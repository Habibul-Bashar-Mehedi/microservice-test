package com.example.userservice.controller;

import com.example.userservice.entity.User;
import com.example.userservice.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/v1/users")
    @ResponseStatus(HttpStatus.CREATED)
    public User createUserV1(@Valid @RequestBody User user) {
        return userService.createUser(user);
    }

    @GetMapping("/v1/users")
    public List<User> getAllUsersV1() {
        return userService.getAllUsers();
    }

    @GetMapping("/v1/users/{id}")
    public User getUserByIdV1(@PathVariable Long id) {
        return userService.getUserById(id);
    }

    @GetMapping("/v1/users/email/{email}")
    public User getUserByEmailV1(@PathVariable String email) {
        return userService.getUserByEmail(email);
    }

    @PostMapping("/v1/users/register")
    @ResponseStatus(HttpStatus.CREATED)
    public User registerV1(@Valid @RequestBody User user, Authentication authentication) {
        String authenticatedEmail = authentication.getName();
        if (user.getEmail() == null || !user.getEmail().equalsIgnoreCase(authenticatedEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only register your own profile"
            );
        }
        return userService.register(user);
    }

    @PatchMapping("/v1/users/{id}/active")
    public User setActiveV1(@PathVariable Long id, @RequestBody ActiveStatusRequest request) {
        return userService.setActive(id, request.active());
    }

    @PatchMapping("/v1/users/{id}/role")
    public User changeRoleV1(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestBody @Valid ChangeRoleRequest request) {
        return userService.setRole(id, request.role(), authHeader);
    }

    public record ActiveStatusRequest(boolean active) {
    }

    public record ChangeRoleRequest(@NotBlank String role) {
    }
}
