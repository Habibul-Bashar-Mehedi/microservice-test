package com.example.orderservice.client;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class UserServiceFeignClientFallback implements UserServiceFeignClient {

    @Override
    public UserProfile getUser(Long id) {
        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "user-service is unavailable (circuit breaker open)"
        );
    }
}