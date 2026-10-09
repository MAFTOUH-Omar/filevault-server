package com.filevault.filevaultserver.response.auth;

public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
