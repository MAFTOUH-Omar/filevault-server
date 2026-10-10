package com.filevault.filevaultserver.action.file;

import com.filevault.filevaultserver.exception.file.UploadNotReceivedException;
import com.filevault.filevaultserver.exception.file.UploadSizeMismatchException;
import com.filevault.filevaultserver.models.StoredFile;
import com.filevault.filevaultserver.models.StoredFileStatus;
import com.filevault.filevaultserver.policy.FilePolicy;
import com.filevault.filevaultserver.repository.file.StoredFileRepository;
import com.filevault.filevaultserver.response.file.FileResponse;
import com.filevault.filevaultserver.security.UserSummaryCacheEvictor;
import com.filevault.filevaultserver.storage.ObjectStorage;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Step 2 of an upload: the client says it has sent the bytes. The client's word is not enough — the
 * object store is asked what it actually holds, and the file only becomes READY if that matches the
 * size that was reserved quota for. Calling it again on a READY file is a harmless no-op.
 */
@Component
public class CompleteUploadAction {

    private final StoredFileRepository storedFileRepository;
    private final FileLedger fileLedger;
    private final FilePolicy filePolicy;
    private final ObjectStorage objectStorage;
    private final UserSummaryCacheEvictor userSummaryCacheEvictor;

    public CompleteUploadAction(
            StoredFileRepository storedFileRepository,
            FileLedger fileLedger,
            FilePolicy filePolicy,
            ObjectStorage objectStorage,
            UserSummaryCacheEvictor userSummaryCacheEvictor) {
        this.storedFileRepository = storedFileRepository;
        this.fileLedger = fileLedger;
        this.filePolicy = filePolicy;
        this.objectStorage = objectStorage;
        this.userSummaryCacheEvictor = userSummaryCacheEvictor;
    }

    public FileResponse execute(UUID callerId, UUID filId) {
        StoredFile file = storedFileRepository.getOrThrow(filId);
        filePolicy.checkCanAccess(file, callerId);
        if (file.getFilStatus() == StoredFileStatus.READY) {
            return FileResponse.from(file);
        }

        OptionalLong storedSizeBytes = objectStorage.sizeOf(file.getFilStorageKey());
        if (storedSizeBytes.isEmpty()) {
            throw new UploadNotReceivedException();
        }
        if (storedSizeBytes.getAsLong() != file.getFilSizeBytes()) {
            // Wrong bytes under a reserved key: refuse them and give the quota back. Row first, object
            // second — if the upload got confirmed in between, the row is READY and must keep its object.
            if (fileLedger.discardPending(file)) {
                userSummaryCacheEvictor.evict(file.getUsrId());
                objectStorage.deleteQuietly(file.getFilStorageKey());
            }
            throw new UploadSizeMismatchException();
        }
        return FileResponse.from(fileLedger.markReady(filId));
    }
}
