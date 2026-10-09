package com.filevault.filevaultserver.response.auth;

import com.filevault.filevaultserver.response.user.UserSummaryResponse;

public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds, UserSummaryResponse user) {
}
