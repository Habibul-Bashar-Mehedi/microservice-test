package com.example.authservice.config;

import com.example.authservice.entity.AuthUser;
import com.example.authservice.repository.AuthUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final AuthUserRepository authUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (!authUserRepository.existsByEmail("admin@example.com")) {
            authUserRepository.save(AuthUser.builder()
                    .name("Admin")
                    .email("admin@example.com")
                    .password(passwordEncoder.encode("admin123"))
                    .role("ADMIN")
                    .build());
        }
    }
}