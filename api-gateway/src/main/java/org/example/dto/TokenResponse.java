package org.example.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
