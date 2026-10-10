package com.filevault.filevaultserver.storage;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.OptionalLong;
import org.slf4j.LoggerFactory;

/**
 * The object store behind file contents (Cloudflare R2 in production). File bytes never pass through
 * this application: clients upload and download directly through short-lived presigned URLs, which
 * are credentials in themselves — never log them.
 */
public interface ObjectStorage {

    /** Where and how the client must PUT the bytes. {@code headers} must be sent exactly as given. */
    record PresignedUpload(URI url, Map<String, String> headers, Instant expiresAt) {
    }

    record PresignedDownload(URI url, Instant expiresAt) {
    }

    PresignedUpload presignUpload(String key, String contentType, long sizeBytes);

    PresignedDownload presignDownload(String key, String downloadFileName);

    /** Size of the stored object, or empty if nothing is stored under {@code key}. */
    OptionalLong sizeOf(String key);

    /** Idempotent: deleting a missing object is not an error. */
    void delete(String key);

    /** For cleanups where the database state has already been settled and a leftover object is only wasted space. */
    default void deleteQuietly(String key) {
        try {
            delete(key);
        } catch (RuntimeException e) {
            LoggerFactory.getLogger(ObjectStorage.class).warn("Could not delete object {} (left orphaned)", key, e);
        }
    }
}
