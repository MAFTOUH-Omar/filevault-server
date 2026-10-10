package com.filevault.filevaultserver.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Escalation ladder for an IP that keeps getting denied: from the {@code warnAfter}-th denial inside
 * {@code window} every denial response carries a warning, and the {@code blacklistAfter}-th one
 * blacklists the IP. Keep {@code warnAfter < blacklistAfter}.
 */
@ConfigurationProperties(prefix = "app.abuse")
public record AbuseProperties(int warnAfter, int blacklistAfter, Duration window) {
}
