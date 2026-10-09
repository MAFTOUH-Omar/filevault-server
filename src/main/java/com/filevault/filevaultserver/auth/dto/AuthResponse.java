package com.filevault.filevaultserver.auth.dto;

public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
