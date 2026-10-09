package com.filevault.filevaultserver.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.signup")
public record SignupProperties(String defaultRole) {
}
