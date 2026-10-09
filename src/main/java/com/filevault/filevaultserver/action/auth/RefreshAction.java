package com.filevault.filevaultserver.action.auth;

import com.filevault.filevaultserver.exception.InvalidRefreshTokenException;
import com.filevault.filevaultserver.models.RefreshToken;
import com.filevault.filevaultserver.repository.RefreshTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RefreshAction {

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenIssuer tokenIssuer;

    public RefreshAction(RefreshTokenRepository refreshTokenRepository, TokenIssuer tokenIssuer) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenIssuer = tokenIssuer;
    }

    @Transactional
    public AuthResult execute(String rawRefreshToken) {
        RefreshToken token = refreshTokenRepository
                .findByTokTokenHash(TokenHasher.sha256Hex(rawRefreshToken))
                .filter(RefreshToken::isActive)
                .orElseThrow(InvalidRefreshTokenException::new);
        token.revoke();
        return tokenIssuer.issueTokens(token.getUser());
    }
}
