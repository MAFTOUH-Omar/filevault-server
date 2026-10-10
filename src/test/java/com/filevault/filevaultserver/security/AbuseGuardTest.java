package com.filevault.filevaultserver.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.filevault.filevaultserver.models.BlacklistedIp;
import com.filevault.filevaultserver.repository.security.BlacklistedIpRepository;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AbuseGuardTest {

    private static final String IP = "203.0.113.7";

    private RedisRateLimiter rateLimiter;
    private BlacklistedIpRepository repository;
    private AbuseGuard guard;

    @BeforeEach
    void setUp() {
        rateLimiter = mock(RedisRateLimiter.class);
        repository = mock(BlacklistedIpRepository.class);
        guard = new AbuseGuard(rateLimiter, repository, new AbuseProperties(3, 6, Duration.ofHours(1)));
    }

    private AbuseGuard.Denial denyNth(long count) {
        when(rateLimiter.increment(anyString(), any())).thenReturn(count);
        return guard.deny(IP, HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
    }

    @Test
    void firstTwoDenialsAreSilent() {
        for (long count = 1; count <= 2; count++) {
            AbuseGuard.Denial denial = denyNth(count);
            assertThat(denial.status()).isEqualTo(403);
            assertThat(denial.code()).isEqualTo("FORBIDDEN");
            assertThat(denial.message()).isEqualTo("Access denied");
        }
        verify(repository, never()).save(any());
    }

    @Test
    void denialsThreeToFiveCarryAWarningWithTheRemainingBudget() {
        assertThat(denyNth(3).message()).contains("Warning").contains("blocked after 3 more");
        assertThat(denyNth(4).message()).contains("blocked after 2 more");
        AbuseGuard.Denial fifth = denyNth(5);
        assertThat(fifth.message()).contains("blocked after 1 more");
        assertThat(fifth.status()).isEqualTo(403);
        assertThat(fifth.code()).isEqualTo("FORBIDDEN");
        verify(repository, never()).save(any());
    }

    @Test
    void sixthDenialBlacklistsTheIp() {
        when(repository.existsBySecIpAddress(IP)).thenReturn(false);

        AbuseGuard.Denial denial = denyNth(6);

        assertThat(denial.status()).isEqualTo(403);
        assertThat(denial.code()).isEqualTo("IP_BLOCKED");
        assertThat(denial.message()).isEqualTo("Access denied");
        verify(repository).save(any(BlacklistedIp.class));
        verify(rateLimiter).reset("abuse:denials:" + IP);
    }

    @Test
    void anAlreadyBlacklistedIpIsNotInsertedTwice() {
        when(repository.existsBySecIpAddress(IP)).thenReturn(true);

        assertThat(denyNth(7).code()).isEqualTo("IP_BLOCKED");

        verify(repository, never()).save(any());
    }

    @Test
    void unauthorizedStatusIsPreservedWhileWarning() {
        when(rateLimiter.increment(anyString(), any())).thenReturn(4L);

        AbuseGuard.Denial denial = guard.deny(IP, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid email or password");

        assertThat(denial.status()).isEqualTo(401);
        assertThat(denial.message()).startsWith("Invalid email or password").contains("Warning");
    }

    @Test
    void redisFailureStillAnswersTheOriginalDenial() {
        when(rateLimiter.increment(anyString(), any())).thenThrow(new IllegalStateException("redis down"));

        AbuseGuard.Denial denial = guard.deny(IP, HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");

        assertThat(denial.status()).isEqualTo(403);
        assertThat(denial.message()).isEqualTo("Access denied");
    }
}
