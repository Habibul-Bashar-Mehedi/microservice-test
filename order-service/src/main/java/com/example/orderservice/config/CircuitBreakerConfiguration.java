package com.example.orderservice.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import java.time.Duration;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

@Configuration
public class CircuitBreakerConfiguration {

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> defaultCircuitBreakerCustomizer() {
        return factory -> {
            factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                    .circuitBreakerConfig(CircuitBreakerConfig.custom()
                            .slidingWindowSize(10)
                            .minimumNumberOfCalls(5)
                            .failureRateThreshold(50)
                            .waitDurationInOpenState(Duration.ofSeconds(10))
                            .permittedNumberOfCallsInHalfOpenState(3)
                            .build())
                    .timeLimiterConfig(TimeLimiterConfig.custom()
                            .timeoutDuration(Duration.ofSeconds(5))
                            .cancelRunningFuture(true)
                            .build())
                    .build());

            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setThreadNamePrefix("cb-");
            executor.setTaskDecorator(CircuitBreakerConfiguration::propagateRequestContext);
            executor.initialize();
            factory.configureExecutorService(executor.getThreadPoolExecutor());
        };
    }

    private static Runnable propagateRequestContext(Runnable task) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return () -> {
            RequestAttributes previous = RequestContextHolder.getRequestAttributes();
            try {
                RequestContextHolder.setRequestAttributes(attributes);
                task.run();
            } finally {
                RequestContextHolder.resetRequestAttributes();
                if (previous != null) {
                    RequestContextHolder.setRequestAttributes(previous);
                }
            }
        };
    }
}