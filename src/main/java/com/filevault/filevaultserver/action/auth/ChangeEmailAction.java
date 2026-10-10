package com.filevault.filevaultserver.action.auth;

import com.filevault.filevaultserver.exception.auth.EmailAlreadyUsedException;
import com.filevault.filevaultserver.exception.auth.InvalidCredentialsException;
import com.filevault.filevaultserver.exception.auth.TooManyProfileChangesException;
import com.filevault.filevaultserver.models.User;
import com.filevault.filevaultserver.repository.user.UserRepository;
import com.filevault.filevaultserver.request.auth.ChangeEmailRequest;
import com.filevault.filevaultserver.response.user.UserSummaryResponse;
import com.filevault.filevaultserver.security.RedisRateLimiter;
import com.filevault.filevaultserver.security.UserSummaryCacheEvictor;
import java.time.Duration;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Changing the account email requires the current password as confirmation (it's the login
 * identifier — a stolen access token alone shouldn't be enough to hijack the account by changing
 * it), and is capped at 3/day per user. The rate-limit check runs *before* the password check and
 * consumes budget even on a wrong password, so this doubles as brute-force throttling for the
 * confirmation step itself.
 */
@Component
public class ChangeEmailAction {

    private static final int DAILY_LIMIT = 3;
    private static final Duration WINDOW = Duration.ofDays(1);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisRateLimiter rateLimiter;
    private final UserSummaryCacheEvictor cacheEvictor;

    public ChangeEmailAction(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RedisRateLimiter rateLimiter,
            UserSummaryCacheEvictor cacheEvictor) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.cacheEvictor = cacheEvictor;
    }

    @Transactional
    public UserSummaryResponse execute(UUID userId, ChangeEmailRequest request) {
        if (!rateLimiter.tryConsume("profile:email:" + userId, DAILY_LIMIT, WINDOW)) {
            throw new TooManyProfileChangesException("email");
        }
        User user = userRepository.getOrThrow(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getUsrPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (!request.newEmail().equalsIgnoreCase(user.getUsrEmail()) && userRepository.existsByUsrEmail(request.newEmail())) {
            throw new EmailAlreadyUsedException(request.newEmail());
        }
        user.setUsrEmail(request.newEmail());
        cacheEvictor.evict(userId);
        return UserSummaryResponse.from(user);
    }
}
