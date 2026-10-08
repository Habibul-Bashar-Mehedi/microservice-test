package com.example.productservice.config;

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
                        .requestMatchers(HttpMethod.POST, "/v1/products").hasAnyRole("MAINTAINER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/v1/products/{id}").hasRole("MAINTAINER")
                        .requestMatchers(HttpMethod.GET, "/v1/products/mine").hasRole("MAINTAINER")
                        .requestMatchers(HttpMethod.GET, "/v1/products/pending/manager").hasRole("MANAGER")
                        .requestMatchers(HttpMethod.POST, "/v1/products/{id}/manager/review").hasRole("MANAGER")
                        .requestMatchers(HttpMethod.GET, "/v1/products/pending/specialist").hasRole("PRODUCT_SPECIALIST")
                        .requestMatchers(HttpMethod.POST, "/v1/products/{id}/specialist/review").hasRole("PRODUCT_SPECIALIST")
                        .requestMatchers(HttpMethod.GET, "/v1/products/pending/salesman").hasRole("SALESMAN")
                        .requestMatchers(HttpMethod.POST, "/v1/products/{id}/salesman/review").hasRole("SALESMAN")
                        .requestMatchers(HttpMethod.GET, "/v1/products/pending/admin").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/v1/products/all")
                        .hasAnyRole("MAINTAINER", "MANAGER", "PRODUCT_SPECIALIST", "SALESMAN", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/v1/products/{id}/admin/review").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/v1/products/{id}/quantity").hasAnyRole("ADMIN", "MANAGER", "MAINTAINER")
                        .requestMatchers(HttpMethod.PUT, "/v1/products/{id}/add-quantity").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/v1/products/{id}/price").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/v1/products/{id}/name").hasRole("ADMIN")
                        .requestMatchers("/v1/**").authenticated()
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