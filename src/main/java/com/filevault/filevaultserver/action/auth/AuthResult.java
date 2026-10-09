package com.filevault.filevaultserver.action.auth;

public record AuthResult(String accessToken, long accessTokenTtlSeconds, String rawRefreshToken) {
}
