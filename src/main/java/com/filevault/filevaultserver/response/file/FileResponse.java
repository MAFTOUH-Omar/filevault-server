package com.filevault.filevaultserver.response.file;

import com.filevault.filevaultserver.models.StoredFile;
import java.time.Instant;
import java.util.UUID;

public record FileResponse(
        UUID id, String fileName, String contentType, long sizeBytes, String status, Instant createdAt) {

    public static FileResponse from(StoredFile file) {
        return new FileResponse(
                file.getFilId(),
                file.getFilOriginalName(),
                file.getFilContentType(),
                file.getFilSizeBytes(),
                file.getFilStatus().name(),
                file.getFilCreatedAt());
    }
}
