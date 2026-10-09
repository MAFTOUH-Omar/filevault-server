package com.filevault.filevaultserver.action.auth;

import com.filevault.filevaultserver.repository.user.UserRepository;
import com.filevault.filevaultserver.response.user.UserSummaryResponse;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backed by the "userSummary" Redis cache (5-minute TTL as a safety net; every mutation that can
 * change what this returns explicitly evicts via UserSummaryCacheEvictor — see CLAUDE.md "Caching").
 * This is the one deliberate exception to "authorization never re-queries the DB": /auth/me's whole
 * point is "what does the DB say right now", unlike the JWT-only authorization checks.
 */
@Component
public class MeAction {

    private final UserRepository userRepository;

    public MeAction(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Cacheable(cacheNames = "userSummary", key = "#userId")
    @Transactional(readOnly = true)
    public UserSummaryResponse execute(UUID userId) {
        return UserSummaryResponse.from(userRepository.getOrThrow(userId));
    }
}
