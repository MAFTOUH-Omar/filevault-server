package com.filevault.filevaultserver.auth;

record AuthResult(String accessToken, long accessTokenTtlSeconds, String rawRefreshToken) {
}
