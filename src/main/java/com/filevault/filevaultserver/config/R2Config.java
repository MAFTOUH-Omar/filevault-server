package com.filevault.filevaultserver.config;

import com.filevault.filevaultserver.storage.StorageProperties;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Cloudflare R2 speaks the S3 API: region is the literal "auto", requests use path-style addressing,
 * and the SDK's default "always add a CRC32 checksum" behaviour (2.30+) is switched off — R2 rejects
 * or mishandles those extra checksum headers/params, notably on presigned PUT URLs.
 */
@Configuration
public class R2Config {

    private static final Region R2_REGION = Region.of("auto");
    private static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(15);

    private static StaticCredentialsProvider credentials(StorageProperties properties) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                properties.r2().accessKeyId(), properties.r2().secretAccessKey()));
    }

    private static S3Configuration pathStyle() {
        return S3Configuration.builder().pathStyleAccessEnabled(true).build();
    }

    @Bean
    public S3Client r2Client(StorageProperties properties) {
        return S3Client.builder()
                .endpointOverride(properties.r2().endpointUri())
                .region(R2_REGION)
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(pathStyle())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                // A hung R2 call must not pin a request thread indefinitely.
                .overrideConfiguration(c -> c.apiCallTimeout(API_CALL_TIMEOUT))
                .build();
    }

    @Bean
    public S3Presigner r2Presigner(StorageProperties properties) {
        return S3Presigner.builder()
                .endpointOverride(properties.r2().endpointUri())
                .region(R2_REGION)
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(pathStyle())
                .build();
    }
}
