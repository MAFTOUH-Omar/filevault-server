package com.filevault.filevaultserver.storage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Validated at startup so a missing bucket/credential fails the boot with a clear message instead of
 * surfacing as a 503 on the first upload.
 */
@Validated
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        @Valid @NotNull R2 r2,
        @Positive long maxFileSizeBytes,
        @NotNull Duration uploadUrlTtl,
        @NotNull Duration downloadUrlTtl,
        @NotNull Duration pendingUploadTtl,
        @Positive long cleanupIntervalSeconds) {

    public record R2(
            @NotBlank String accountId,
            @NotBlank String accessKeyId,
            @NotBlank String secretAccessKey,
            @NotBlank String bucket,
            String endpoint) {

        /** Cloudflare's per-account S3 endpoint, unless {@code endpoint} overrides it (tests, S3-compatible stand-ins). */
        public URI endpointUri() {
            if (endpoint != null && !endpoint.isBlank()) {
                return URI.create(endpoint.trim());
            }
            return URI.create("https://" + accountId + ".r2.cloudflarestorage.com");
        }

        // The secret must never reach a log line through an accidental toString().
        @Override
        public String toString() {
            return "R2[accountId=" + accountId + ", bucket=" + bucket + ", accessKeyId=***, secretAccessKey=***]";
        }
    }
}
