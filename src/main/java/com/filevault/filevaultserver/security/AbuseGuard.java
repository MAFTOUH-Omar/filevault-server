package com.filevault.filevaultserver.security;

import com.filevault.filevaultserver.models.BlacklistedIp;
import com.filevault.filevaultserver.repository.security.BlacklistedIpRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Counts "denied" responses (bad credentials, invalid tokens, missing permissions) per IP and
 * escalates: silent, then a warning in the error body, then the IP lands in sec_blacklisted_ips —
 * after which IpBlacklistFilter rejects everything from it, login included.
 *
 * Both denial paths go through {@link #deny}: the controller-side ones (GlobalExceptionHandler) and
 * the filter-chain ones (RestAuthenticationEntryPoint / RestAccessDeniedHandler), so the counter
 * can't be dodged by picking an endpoint that is denied by one path rather than the other.
 */
@Component
public class AbuseGuard {

    private static final Logger log = LoggerFactory.getLogger(AbuseGuard.class);
    private static final String KEY_PREFIX = "abuse:denials:";
    private static final String BLACKLIST_REASON = "Auto-blacklisted: repeated unauthorized or forbidden requests";

    /** What the caller should send back: status, machine code and (possibly warning-extended) message. */
    public record Denial(int status, String code, String message) {
    }

    private final RedisRateLimiter rateLimiter;
    private final BlacklistedIpRepository blacklistedIpRepository;
    private final AbuseProperties properties;

    public AbuseGuard(
            RedisRateLimiter rateLimiter,
            BlacklistedIpRepository blacklistedIpRepository,
            AbuseProperties properties) {
        this.rateLimiter = rateLimiter;
        this.blacklistedIpRepository = blacklistedIpRepository;
        this.properties = properties;
    }

    public Denial deny(String ip, HttpStatus status, String code, String message) {
        long denials = countDenial(ip);
        if (denials >= properties.blacklistAfter()) {
            blacklist(ip);
            return new Denial(HttpStatus.FORBIDDEN.value(), "IP_BLOCKED", "Access denied");
        }
        if (denials >= properties.warnAfter()) {
            long remaining = properties.blacklistAfter() - denials;
            return new Denial(
                    status.value(),
                    code,
                    message + ". Warning: repeated unauthorized requests detected; your IP will be blocked after "
                            + remaining + " more.");
        }
        return new Denial(status.value(), code, message);
    }

    // A Redis hiccup must not turn a 401/403 into a 500 — the denial is still a denial.
    private long countDenial(String ip) {
        try {
            return rateLimiter.increment(KEY_PREFIX + ip, properties.window());
        } catch (RuntimeException e) {
            log.warn("Could not record denial for {}", ip, e);
            return 0L;
        }
    }

    private void blacklist(String ip) {
        try {
            if (!blacklistedIpRepository.existsBySecIpAddress(ip)) {
                blacklistedIpRepository.save(new BlacklistedIp(ip, BLACKLIST_REASON));
                log.warn("IP {} blacklisted after repeated denied requests", ip);
            }
            // Fresh start if an admin later lifts the ban, instead of re-banning on the very next denial.
            rateLimiter.reset(KEY_PREFIX + ip);
        } catch (DataIntegrityViolationException alreadyBlacklistedConcurrently) {
            log.debug("IP {} was blacklisted concurrently", ip);
        } catch (RuntimeException e) {
            log.error("Could not blacklist {}", ip, e);
        }
    }
}
