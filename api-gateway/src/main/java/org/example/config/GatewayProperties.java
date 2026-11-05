package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(Jwt jwt, Map<String, User> users) {

    public GatewayProperties {
        users = users == null ? Map.of() : users;
    }

    public record Jwt(String secret, long ttlMinutes) {
    }

    public record User(String password, UUID customerId, List<String> roles) {

        public User {
            roles = roles == null ? List.of() : roles;
        }
    }
}
