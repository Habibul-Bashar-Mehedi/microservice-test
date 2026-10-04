package com.example.orderservice.client;

import com.example.orderservice.config.HttpOperation;
import com.example.orderservice.config.UserServiceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@RequiredArgsConstructor
public class RestClientUserClient implements UserClient {

    private final @Qualifier("userServiceRestClient") RestClient userServiceRestClient;
    private final UserServiceProperties properties;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    @Override
    public boolean isActive(Long userId) {
        return circuitBreakerFactory.create("userService").run(
                () -> doIsActive(userId),
                throwable -> false);
    }

    private boolean doIsActive(Long userId) {
        HttpOperation operation = properties.getGetUser();

        UserStatus status;
        try {
            status = userServiceRestClient.method(operation.getMethod())
                    .uri(operation.getPath(), userId)
                    .retrieve()
                    .body(UserStatus.class);
        } catch (RestClientResponseException e) {
            return false;
        }

        return status != null && status.active();
    }

    @Override
    public String getEmail(Long userId) {
        return circuitBreakerFactory.create("userService").run(
                () -> doGetEmail(userId),
                throwable -> null);
    }

    private String doGetEmail(Long userId) {
        HttpOperation operation = properties.getGetUser();

        try {
            UserProfile profile = userServiceRestClient.method(operation.getMethod())
                    .uri(operation.getPath(), userId)
                    .retrieve()
                    .body(UserProfile.class);
            return profile != null ? profile.email() : null;
        } catch (RestClientResponseException e) {
            return null;
        }
    }

    @Override
    public String getName(Long userId) {
        return circuitBreakerFactory.create("userService").run(
                () -> doGetName(userId),
                throwable -> null);
    }

    private String doGetName(Long userId) {
        HttpOperation operation = properties.getGetUser();

        try {
            UserProfile profile = userServiceRestClient.method(operation.getMethod())
                    .uri(operation.getPath(), userId)
                    .retrieve()
                    .body(UserProfile.class);
            return profile != null ? profile.name() : null;
        } catch (RestClientResponseException e) {
            return null;
        }
    }

    public record UserStatus(boolean active) {
    }

    public record UserProfile(Long id, String name, String email, boolean active) {
    }
}