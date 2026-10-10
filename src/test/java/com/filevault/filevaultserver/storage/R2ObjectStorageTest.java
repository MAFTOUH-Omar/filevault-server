package com.filevault.filevaultserver.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.filevault.filevaultserver.config.R2Config;
import com.filevault.filevaultserver.exception.file.StorageUnavailableException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.OptionalLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class R2ObjectStorageTest {

    private static final String BUCKET = "filevault-test";

    private FakeS3Server fakeS3;
    private S3Client s3Client;
    private S3Presigner presigner;
    private R2ObjectStorage storage;

    @BeforeEach
    void setUp() throws Exception {
        fakeS3 = new FakeS3Server(0);
        StorageProperties properties = new StorageProperties(
                new StorageProperties.R2("acct", "AKIATESTKEY", "test-secret-key", BUCKET, fakeS3.endpoint()),
                209_715_200L,
                Duration.ofMinutes(15),
                Duration.ofMinutes(5),
                Duration.ofMinutes(30),
                600L);
        R2Config config = new R2Config();
        s3Client = config.r2Client(properties);
        presigner = config.r2Presigner(properties);
        storage = new R2ObjectStorage(s3Client, presigner, properties);
    }

    @AfterEach
    void tearDown() {
        s3Client.close();
        presigner.close();
        fakeS3.close();
    }

    @Test
    void presignedUploadIsPathStyleSignedAndFreeOfSdkChecksumParams() {
        ObjectStorage.PresignedUpload upload = storage.presignUpload("user-1/file-1", "image/png", 1234);

        URI url = upload.url();
        assertThat(url.getPath()).isEqualTo("/" + BUCKET + "/user-1/file-1");
        assertThat(url.getQuery()).contains("X-Amz-Signature=").contains("X-Amz-Expires=900");
        // R2 rejects the SDK's default CRC32 checksum additions (they would make every upload fail).
        assertThat(url.getQuery().toLowerCase()).doesNotContain("checksum");
        assertThat(upload.headers()).containsEntry("content-type", "image/png");
        assertThat(upload.headers().keySet()).doesNotContain("host", "content-length");
        assertThat(upload.expiresAt()).isAfter(java.time.Instant.now());
    }

    @Test
    void presignedUploadUrlAcceptsTheBytes() throws Exception {
        ObjectStorage.PresignedUpload upload = storage.presignUpload("user-1/file-2", "text/plain", 5);

        HttpRequest.Builder request = HttpRequest.newBuilder(upload.url())
                .PUT(HttpRequest.BodyPublishers.ofString("hello"));
        upload.headers().forEach(request::header);
        HttpResponse<String> response = HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(storage.sizeOf("user-1/file-2")).hasValue(5);
    }

    @Test
    void presignedDownloadForcesAttachmentUnderTheOriginalNameEvenWhenItIsNasty() {
        ObjectStorage.PresignedDownload download = storage.presignDownload("user-1/file-1", "rapport \"final\"\r\nX-Evil: 1 é.pdf");

        String query = java.net.URLDecoder.decode(download.url().getRawQuery(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(query).contains("response-content-disposition=attachment");
        // No raw CR/LF may survive into the header value, or it could inject headers.
        assertThat(download.url().getRawQuery()).doesNotContain("%0D").doesNotContain("%0A");
        assertThat(download.url().getPath()).isEqualTo("/" + BUCKET + "/user-1/file-1");
    }

    @Test
    void sizeOfReportsStoredSizeOrEmpty() {
        fakeS3.put("/" + BUCKET + "/user-1/there", new byte[42]);

        assertThat(storage.sizeOf("user-1/there")).isEqualTo(OptionalLong.of(42));
        assertThat(storage.sizeOf("user-1/missing")).isEmpty();
    }

    @Test
    void deleteRemovesTheObjectAndIsIdempotent() {
        fakeS3.put("/" + BUCKET + "/user-1/doomed", new byte[3]);

        storage.delete("user-1/doomed");
        storage.delete("user-1/doomed");

        assertThat(fakeS3.contains("/" + BUCKET + "/user-1/doomed")).isFalse();
    }

    @Test
    void sdkFailuresBecomeAClientSafeUnavailableError() {
        fakeS3.failWith(500);

        assertThatThrownBy(() -> storage.sizeOf("user-1/x"))
                .isInstanceOf(StorageUnavailableException.class)
                .hasMessageNotContaining("500")
                .hasMessageNotContaining("127.0.0.1");
        assertThatThrownBy(() -> storage.delete("user-1/x")).isInstanceOf(StorageUnavailableException.class);
    }

    @Test
    void deleteQuietlySwallowsStorageFailures() {
        fakeS3.failWith(500);

        storage.deleteQuietly("user-1/x"); // must not throw
    }
}
