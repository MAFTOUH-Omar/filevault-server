package com.filevault.filevaultserver.request.file;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record InitiateUploadRequest(
        // Only ever metadata (the object key is generated), but it ends up in a Content-Disposition
        // header on download, so path separators, control characters and dot-only names are refused.
        @NotBlank @Size(max = 255)
        @Pattern(regexp = "^(?!\\.+$)[^\\p{Cntrl}/\\\\]+$", message = "must not contain path separators or control characters")
        String fileName,
        // A bare type/subtype, no parameters: this exact value becomes a signed header the client must resend.
        @NotBlank @Size(max = 255)
        @Pattern(
                regexp = "^[A-Za-z0-9][A-Za-z0-9!#$&^_.+-]*/[A-Za-z0-9][A-Za-z0-9!#$&^_.+-]*$",
                message = "must be a valid MIME type such as image/png")
        String contentType,
        @NotNull @PositiveOrZero Long sizeBytes) {
}
