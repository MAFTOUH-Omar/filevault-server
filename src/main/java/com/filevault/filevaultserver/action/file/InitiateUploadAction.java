package com.filevault.filevaultserver.action.file;

import com.filevault.filevaultserver.exception.file.FileTooLargeException;
import com.filevault.filevaultserver.models.StoredFile;
import com.filevault.filevaultserver.request.file.InitiateUploadRequest;
import com.filevault.filevaultserver.response.file.FileResponse;
import com.filevault.filevaultserver.response.file.UploadTicketResponse;
import com.filevault.filevaultserver.security.UserSummaryCacheEvictor;
import com.filevault.filevaultserver.storage.ObjectStorage;
import com.filevault.filevaultserver.storage.StorageProperties;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Step 1 of an upload: check the size limit, reserve quota and record a PENDING file, then hand back
 * a presigned PUT URL. The reserved bytes count towards the user's usage straight away, so parallel
 * uploads cannot together overshoot the quota; if the client never finishes, the sweeper gives them back.
 */
@Component
public class InitiateUploadAction {

    private final FileLedger fileLedger;
    private final ObjectStorage objectStorage;
    private final StorageProperties storageProperties;
    private final UserSummaryCacheEvictor userSummaryCacheEvictor;

    public InitiateUploadAction(
            FileLedger fileLedger,
            ObjectStorage objectStorage,
            StorageProperties storageProperties,
            UserSummaryCacheEvictor userSummaryCacheEvictor) {
        this.fileLedger = fileLedger;
        this.objectStorage = objectStorage;
        this.storageProperties = storageProperties;
        this.userSummaryCacheEvictor = userSummaryCacheEvictor;
    }

    public UploadTicketResponse execute(UUID usrId, InitiateUploadRequest request) {
        long sizeBytes = request.sizeBytes();
        if (sizeBytes > storageProperties.maxFileSizeBytes()) {
            throw new FileTooLargeException(storageProperties.maxFileSizeBytes());
        }

        StoredFile file = fileLedger.reserve(
                usrId, request.fileName().strip(), request.contentType().toLowerCase(Locale.ROOT), sizeBytes);
        userSummaryCacheEvictor.evict(usrId);

        ObjectStorage.PresignedUpload upload;
        try {
            upload = objectStorage.presignUpload(file.getFilStorageKey(), file.getFilContentType(), sizeBytes);
        } catch (RuntimeException e) {
            // No usable URL was issued, so don't keep the reservation hostage until the sweeper runs.
            fileLedger.discardPending(file);
            userSummaryCacheEvictor.evict(usrId);
            throw e;
        }
        return new UploadTicketResponse(
                FileResponse.from(file), "PUT", upload.url().toString(), upload.headers(), upload.expiresAt());
    }
}
