package org.example.controller;

import org.example.config.GatewayProperties;
import org.example.dto.LoginRequest;
import org.example.dto.TokenResponse;
import org.example.security.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Demo login: users come from the configuration, their passwords are hashed with BCrypt at start-up
 * and compared with BCrypt. A real system would delegate this to an identity provider.
 * BCrypt is deliberately slow and runs on the request thread here, which is fine for a demo.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final GatewayProperties properties;
    private final TokenService tokenService;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final Map<String, String> passwordHashes = new HashMap<>();
    private final String dummyHash;

    public AuthController(GatewayProperties properties, TokenService tokenService) {
        this.properties = properties;
        this.tokenService = tokenService;
        properties.users().forEach((name, user) -> passwordHashes.put(name, encoder.encode(user.password())));
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {
        String username = request.username();
        String password = request.password() == null ? "" : request.password();
        String hash = username == null ? null : passwordHashes.get(username);

        // always run one BCrypt check, so an unknown user takes as long as a wrong password
        boolean passwordMatches = encoder.matches(password, hash != null ? hash : dummyHash);
        if (hash == null || !passwordMatches) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        GatewayProperties.User user = properties.users().get(username);
        String token = tokenService.issue(username, user.customerId(), user.roles());
        return ResponseEntity.ok(new TokenResponse(token, "Bearer", tokenService.ttlSeconds()));
    }
}
