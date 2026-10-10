package com.filevault.filevaultserver.storage;

import com.filevault.filevaultserver.exception.file.StorageUnavailableException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

/**
 * Talks to R2 through its S3-compatible API. Any SDK failure is logged here (with its real cause) and
 * rethrown as {@link StorageUnavailableException}, whose message is client-safe, so callers never see
 * SDK/endpoint details.
 */
@Component
public class R2ObjectStorage implements ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(R2ObjectStorage.class);

    // Presigned headers a client must not (or cannot) set itself.
    private static final java.util.Set<String> UNSENT_HEADERS = java.util.Set.of("host", "content-length");

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final StorageProperties properties;

    public R2ObjectStorage(S3Client s3Client, S3Presigner presigner, StorageProperties properties) {
        this.s3Client = s3Client;
        this.presigner = presigner;
        this.properties = properties;
    }

    @Override
    public PresignedUpload presignUpload(String key, String contentType, long sizeBytes) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(properties.r2().bucket())
                    .key(key)
                    .contentType(contentType)
                    .contentLength(sizeBytes)
                    .build();
            PresignedPutObjectRequest presigned = presigner.presignPutObject(
                    r -> r.signatureDuration(properties.uploadUrlTtl()).putObjectRequest(request));

            Map<String, String> headers = new LinkedHashMap<>();
            presigned.signedHeaders().forEach((name, values) -> {
                if (!UNSENT_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                    headers.put(name, String.join(",", values));
                }
            });
            return new PresignedUpload(toUri(presigned.url().toString()), headers, presigned.expiration());
        } catch (SdkException e) {
            throw unavailable("presign upload", e);
        }
    }

    @Override
    public PresignedDownload presignDownload(String key, String downloadFileName) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(properties.r2().bucket())
                    .key(key)
                    .responseContentDisposition(ContentDisposition.attachment()
                            .filename(downloadFileName, StandardCharsets.UTF_8)
                            .build()
                            .toString())
                    .build();
            PresignedGetObjectRequest presigned = presigner.presignGetObject(
                    r -> r.signatureDuration(properties.downloadUrlTtl()).getObjectRequest(request));
            return new PresignedDownload(toUri(presigned.url().toString()), presigned.expiration());
        } catch (SdkException e) {
            throw unavailable("presign download", e);
        }
    }

    @Override
    public OptionalLong sizeOf(String key) {
        try {
            long size = s3Client
                    .headObject(r -> r.bucket(properties.r2().bucket()).key(key))
                    .contentLength();
            return OptionalLong.of(size);
        } catch (NoSuchKeyException e) {
            return OptionalLong.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return OptionalLong.empty();
            }
            throw unavailable("head object", e);
        } catch (SdkException e) {
            throw unavailable("head object", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3Client.deleteObject(r -> r.bucket(properties.r2().bucket()).key(key));
        } catch (SdkException e) {
            throw unavailable("delete object", e);
        }
    }

    private static java.net.URI toUri(String url) {
        return java.net.URI.create(url);
    }

    private static StorageUnavailableException unavailable(String operation, SdkException cause) {
        log.error("Object storage call failed: {}", operation, cause);
        return new StorageUnavailableException();
    }
}
