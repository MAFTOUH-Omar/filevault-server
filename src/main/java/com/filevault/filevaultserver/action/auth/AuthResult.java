package com.filevault.filevaultserver.action.auth;

import com.filevault.filevaultserver.response.user.UserSummaryResponse;

public record AuthResult(String accessToken, long accessTokenTtlSeconds, String rawRefreshToken, UserSummaryResponse user) {
}
