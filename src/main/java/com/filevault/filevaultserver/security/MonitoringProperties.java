package com.filevault.filevaultserver.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credentials for the actuator endpoints beyond {@code health}/{@code info} (HTTP Basic, role
 * {@code MONITOR}) and the opt-in request recorder. An empty password means nobody can log in: only the
 * public {@code health}/{@code info} endpoints stay reachable.
 */
@ConfigurationProperties(prefix = "app.monitoring")
public record MonitoringProperties(String username, String password, boolean recordRequests) {

    static final int MIN_PASSWORD_LENGTH = 16;

    public MonitoringProperties {
        if (username == null || username.isBlank()) {
            username = "monitor";
        }
        if (password == null) {
            password = "";
        }
        if (!password.isEmpty() && password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "app.monitoring.password (MONITORING_PASSWORD) must be at least " + MIN_PASSWORD_LENGTH
                            + " characters, or empty to disable monitoring logins");
        }
    }

    public boolean loginEnabled() {
        return !password.isEmpty();
    }

    /** Never print the password. */
    @Override
    public String toString() {
        return "MonitoringProperties[username=" + username + ", password=****, recordRequests=" + recordRequests + "]";
    }
}
