package com.example.userservice.controller;

import com.example.userservice.entity.User;
import com.example.userservice.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
}
