package com.example.userservice.service;

import com.example.userservice.entity.User;
import com.example.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    @CacheEvict(value = {"users"}, allEntries = true)
    public User createUser(User user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "User with email " + user.getEmail() + " already exists"
            );
        }

        user.setActive(false);
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
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found with email: " + email
                ));
    }

    @Transactional
    @CacheEvict(value = {"users"}, allEntries = true)
    public User register(User user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "User with email " + user.getEmail() + " already exists"
            );
        }

        user.setActive(false);
        return userRepository.save(user);
    }

    @Transactional
    @CacheEvict(value = {"users", "userById"}, allEntries = true)
    public User setActive(Long id, boolean active) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found with id: " + id
                ));

        user.setActive(active);
        return userRepository.save(user);
    }
}