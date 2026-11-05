package org.example.security;

import org.example.config.GatewayProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";

    private static TokenService service(String secret) {
        return new TokenService(new GatewayProperties(new GatewayProperties.Jwt(secret, 60), Map.of()));
    }

    private static JwtDecoder decoder(String secret) {
        return NimbusJwtDecoder.withSecretKey(TokenService.secretKey(secret))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Test
    void issuedTokenCarriesTheUserAndVerifiesWithTheSameSecret() {
        UUID customerId = UUID.randomUUID();

        String token = service(SECRET).issue("alice", customerId, List.of("USER"));
        Jwt jwt = decoder(SECRET).decode(token);

        assertThat(jwt.getSubject()).isEqualTo("alice");
        assertThat(jwt.getClaimAsString("customerId")).isEqualTo(customerId.toString());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(60));
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = service(SECRET).issue("alice", UUID.randomUUID(), List.of("USER"));
        JwtDecoder other = decoder("another-secret-another-secret-123456");

        assertThatThrownBy(() -> other.decode(token)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void secretShorterThan32BytesIsRejected() {
        assertThatThrownBy(() -> service("too-short")).isInstanceOf(IllegalStateException.class);
    }
}
