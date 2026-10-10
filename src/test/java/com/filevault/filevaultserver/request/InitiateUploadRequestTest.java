package com.filevault.filevaultserver.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.filevault.filevaultserver.request.file.InitiateUploadRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class InitiateUploadRequestTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean valid(String fileName, String contentType, Long sizeBytes) {
        return VALIDATOR.validate(new InitiateUploadRequest(fileName, contentType, sizeBytes)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"report.pdf", "mon fichier é.docx", ".gitignore", "archive.tar.gz", "a"})
    void acceptsOrdinaryFileNames(String name) {
        assertThat(valid(name, "application/pdf", 10L)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "..", ".", "../etc/passwd", "a/b.txt", "a\\b.txt", "line\nbreak.txt", "nul\u0000.txt"})
    void rejectsPathLikeOrControlCharacterNames(String name) {
        assertThat(valid(name, "application/pdf", 10L)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/png", "application/vnd.ms-excel", "text/plain", "application/x-tar+gzip"})
    void acceptsBareMimeTypes(String type) {
        assertThat(valid("a.bin", type, 10L)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "png", "text/plain; charset=utf-8", "text/", "/plain", "text/pl ain", "text/plain\r\nX: 1"})
    void rejectsAnythingButBareMimeTypes(String type) {
        assertThat(valid("a.bin", type, 10L)).isFalse();
    }

    @ParameterizedTest
    @MethodSource("badSizes")
    void rejectsMissingOrNegativeSizes(Long size) {
        assertThat(valid("a.bin", "text/plain", size)).isFalse();
    }

    static Stream<Long> badSizes() {
        return Stream.of(null, -1L, Long.MIN_VALUE);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, 209_715_200L})
    void acceptsEmptyAndLargeSizes(long size) {
        assertThat(valid("a.bin", "text/plain", size)).isTrue();
    }
}
