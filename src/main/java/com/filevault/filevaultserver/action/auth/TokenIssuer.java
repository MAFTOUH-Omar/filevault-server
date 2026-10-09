package com.filevault.filevaultserver.action.auth;

import com.filevault.filevaultserver.models.RefreshToken;
import com.filevault.filevaultserver.models.User;
import com.filevault.filevaultserver.repository.RefreshTokenRepository;
import com.filevault.filevaultserver.security.JwtProperties;
import com.filevault.filevaultserver.security.JwtService;
import com.filevault.filevaultserver.security.RefreshTokenProperties;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import org.springframework.stereotype.Component;

/** Shared by every action that must hand a user a fresh access + refresh token pair. */
@Component
class TokenIssuer {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final RefreshTokenProperties refreshTokenProperties;

    TokenIssuer(
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService,
            JwtProperties jwtProperties,
            RefreshTokenProperties refreshTokenProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.refreshTokenProperties = refreshTokenProperties;
    }

    AuthResult issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = generateOpaqueToken();
        RefreshToken refreshToken = new RefreshToken(
                user, TokenHasher.sha256Hex(rawRefreshToken), Instant.now().plus(refreshTokenProperties.ttl()));
        refreshTokenRepository.save(refreshToken);
        return new AuthResult(accessToken, jwtProperties.accessTokenTtl().toSeconds(), rawRefreshToken);
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
