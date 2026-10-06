package com.example.logservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class SecurityConfigTest {

    private static final String SECRET =
            "7a4f0c9b2e5d8a1c3f6b9e2d4a7c0f3b6e9a2d4c7f0b3e6a9d2c4f7b0e3a6c";

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void jwtDecoder_decodesTokenSignedWithConfiguredSecret() {
        JwtDecoder decoder = securityConfig.jwtDecoder(SECRET);
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA384");
        NimbusJwtEncoder encoder = NimbusJwtEncoder.withSecretKey(key)
                .algorithm(MacAlgorithm.HS384)
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(
                        JwtClaimsSet.builder().subject("alice@example.com").claim("role", "ADMIN").build()))
                .getTokenValue();

        Jwt decoded = decoder.decode(token);

        assertThat(decoded.getSubject()).isEqualTo("alice@example.com");
        assertThat(decoded.getClaimAsString("role")).isEqualTo("ADMIN");
    }

    @Test
    void jwtAuthenticationConverter_usesSubjectAsPrincipalAndRoleClaimAsAuthority() {
        AbstractAuthenticationToken token = securityConfig.jwtAuthenticationConverter()
                .convert(jwt(Map.of("sub", "alice", "role", "ADMIN")));

        assertThat(token.getName()).isEqualTo("alice");
        assertThat(token.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_ADMIN");
    }

    @Test
    void jwtAuthenticationConverter_defaultsToUserRoleWhenClaimMissingOrBlank() {
        AbstractAuthenticationToken missing = securityConfig.jwtAuthenticationConverter()
                .convert(jwt(Map.of("sub", "bob")));
        AbstractAuthenticationToken blank = securityConfig.jwtAuthenticationConverter()
                .convert(jwt(Map.of("sub", "carol", "role", "  ")));

        assertThat(missing.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_USER")
                .doesNotContain("ROLE_ADMIN");
        assertThat(blank.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_USER")
                .doesNotContain("ROLE_ADMIN");
    }

    private Jwt jwt(Map<String, Object> claims) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "HS384"), claims);
    }
}
