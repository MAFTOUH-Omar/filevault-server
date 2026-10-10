package com.filevault.filevaultserver.action.auth;

import com.filevault.filevaultserver.exception.auth.InvalidCredentialsException;
import com.filevault.filevaultserver.exception.auth.TooManyProfileChangesException;
import com.filevault.filevaultserver.models.RefreshToken;
import com.filevault.filevaultserver.models.User;
import com.filevault.filevaultserver.repository.auth.RefreshTokenRepository;
import com.filevault.filevaultserver.repository.user.UserRepository;
import com.filevault.filevaultserver.request.auth.ChangePasswordRequest;
import com.filevault.filevaultserver.security.RedisRateLimiter;
import java.time.Duration;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Same confirmation + 3/day budget as ChangeEmailAction. Also revokes every other active refresh
 * token for this user — the standard "changing your password logs out every other session" security
 * behavior, since the old password may have been compromised, which is exactly why it's being changed.
 */
@Component
public class ChangePasswordAction {

    private static final int DAILY_LIMIT = 3;
    private static final Duration WINDOW = Duration.ofDays(1);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisRateLimiter rateLimiter;

    public ChangePasswordAction(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            RedisRateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public void execute(UUID userId, ChangePasswordRequest request) {
        if (!rateLimiter.tryConsume("profile:password:" + userId, DAILY_LIMIT, WINDOW)) {
            throw new TooManyProfileChangesException("password");
        }
        User user = userRepository.getOrThrow(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getUsrPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        user.setUsrPasswordHash(passwordEncoder.encode(request.newPassword()));
        for (RefreshToken token : refreshTokenRepository.findByUser_UsrIdAndTokRevokedAtIsNull(userId)) {
            token.revoke();
        }
    }
}
