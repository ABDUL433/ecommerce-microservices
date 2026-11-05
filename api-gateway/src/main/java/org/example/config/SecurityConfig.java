package org.example.config;

import org.example.security.TokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtGrantedAuthoritiesConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ReactiveJwtDecoder jwtDecoder) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        // open: log in and read metrics
                        .pathMatchers("/auth/login", "/actuator/**").permitAll()
                        // any logged-in user
                        .pathMatchers("/api/orders", "/api/orders/**").authenticated()
                        .pathMatchers(HttpMethod.GET, "/api/products", "/api/products/**", "/api/inventory/**").authenticated()
                        // administrators only: create products, add stock, read payments
                        .pathMatchers("/api/products", "/api/products/**", "/api/inventory/**", "/api/payments/**").hasRole("ADMIN")
                        // everything else (for example /api/reservations) is not exposed
                        .anyExchange().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt
                        .jwtDecoder(jwtDecoder)
                        .jwtAuthenticationConverter(rolesConverter())))
                .build();
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder(GatewayProperties properties) {
        return NimbusReactiveJwtDecoder
                .withSecretKey(TokenService.secretKey(properties.jwt().secret()))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /** Turns the "roles" claim of the token into ROLE_... authorities. */
    private ReactiveJwtAuthenticationConverter rolesConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new ReactiveJwtGrantedAuthoritiesConverterAdapter(authorities));
        return converter;
    }
}
