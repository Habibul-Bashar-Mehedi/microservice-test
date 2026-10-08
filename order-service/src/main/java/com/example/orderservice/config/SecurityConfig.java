package com.example.orderservice.config;

import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/v1/orders").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/v2/orders").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/v3/orders").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/v1/orders/user/{userId}").hasRole("USER")
                        .requestMatchers(HttpMethod.POST, "/v1/orders/{id}/cancel").hasRole("USER")
                        .requestMatchers(HttpMethod.GET, "/v1/orders").hasAnyRole("ADMIN", "MANAGER", "MAINTAINER")
                        .requestMatchers(HttpMethod.POST, "/v1/orders/{id}/confirm").hasAnyRole("ADMIN", "MANAGER", "MAINTAINER")
                        .requestMatchers(HttpMethod.POST, "/v2/orders/{id}/confirm").hasAnyRole("ADMIN", "MANAGER", "MAINTAINER")
                        .requestMatchers(HttpMethod.POST, "/v3/orders/{id}/confirm").hasAnyRole("ADMIN", "MANAGER", "MAINTAINER")
                        .requestMatchers("/v1/cart", "/v1/cart/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/v1/**", "/v2/**", "/v3/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${jwt.secret}") String secret) {
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA384");
        return NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS384)
                .build();
    }

    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("sub");
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            String resolvedRole = (role == null || role.isBlank()) ? "USER" : role;
            List<GrantedAuthority> authorities = new java.util.ArrayList<>(
                    List.of(new SimpleGrantedAuthority("ROLE_" + resolvedRole)));
            return authorities;
        });
        return converter;
    }
}