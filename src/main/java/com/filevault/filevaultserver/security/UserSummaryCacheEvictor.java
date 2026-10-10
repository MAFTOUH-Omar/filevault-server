package com.filevault.filevaultserver.security;

import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

/**
 * A dedicated bean for this one method, because Spring's {@code @CacheEvict} is proxy-based: calling
 * it via {@code this.evict(...)} from inside another component would bypass the proxy and silently
 * do nothing. Anything that changes a user's roles/permissions/profile must call this (injected as
 * a collaborator) afterward — see MeAction's {@code @Cacheable} and the "Caching" note in CLAUDE.md
 * for the full list of call sites.
 */
@Component
public class UserSummaryCacheEvictor {

    @CacheEvict(cacheNames = "userSummary", key = "#userId")
    public void evict(UUID userId) {
        // no-op body: the eviction is the point, done by the @CacheEvict proxy
    }
}
