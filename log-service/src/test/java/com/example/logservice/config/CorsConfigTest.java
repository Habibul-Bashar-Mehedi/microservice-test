package com.example.logservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

class CorsConfigTest {

    @Test
    void corsConfigurationSource_registersExpectedPolicyForAllPaths() {
        UrlBasedCorsConfigurationSource source =
                (UrlBasedCorsConfigurationSource) new CorsConfig().corsConfigurationSource();

        CorsConfiguration configuration = source.getCorsConfigurations().get("/**");
        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).containsExactly("http://localhost:4200");
        assertThat(configuration.getAllowedMethods())
                .containsExactly("GET", "POST", "PUT", "PATCH", "DELETE");
        assertThat(configuration.getAllowedHeaders()).containsExactly("Authorization", "Content-Type");
    }
}
