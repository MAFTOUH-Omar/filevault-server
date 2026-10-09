package com.filevault.filevaultserver.action.auth;

import com.filevault.filevaultserver.models.RefreshToken;
import com.filevault.filevaultserver.repository.RefreshTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LogoutAction {

    private final RefreshTokenRepository refreshTokenRepository;

    public LogoutAction(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public void execute(String rawRefreshToken) {
        refreshTokenRepository.findByTokTokenHash(TokenHasher.sha256Hex(rawRefreshToken)).ifPresent(RefreshToken::revoke);
    }
}
