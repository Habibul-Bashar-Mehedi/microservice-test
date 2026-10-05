package com.example.orderservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", contextId = "v3UserService", fallback = UserServiceFeignClientFallback.class)
public interface UserServiceFeignClient {

    @GetMapping("/v1/users/{id}")
    UserProfile getUser(@PathVariable("id") Long id);
}