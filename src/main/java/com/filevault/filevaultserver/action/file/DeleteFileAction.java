package com.filevault.filevaultserver.action.file;

import com.filevault.filevaultserver.models.StoredFile;
import com.filevault.filevaultserver.policy.FilePolicy;
import com.filevault.filevaultserver.repository.file.StoredFileRepository;
import com.filevault.filevaultserver.security.UserSummaryCacheEvictor;
import com.filevault.filevaultserver.storage.ObjectStorage;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Object first, row second. If R2 is down the call fails with 503 and nothing changed, so it can simply
 * be retried; deleting an object is idempotent, so a retry after a failure between the two steps also
 * converges. The opposite order could leave an invisible, forever-billed orphan object.
 */
@Component
public class DeleteFileAction {

    private final StoredFileRepository storedFileRepository;
    private final FileLedger fileLedger;
    private final FilePolicy filePolicy;
    private final ObjectStorage objectStorage;
    private final UserSummaryCacheEvictor userSummaryCacheEvictor;

    public DeleteFileAction(
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

    public void execute(UUID callerId, UUID filId) {
        StoredFile file = storedFileRepository.getOrThrow(filId);
        filePolicy.checkCanAccess(file, callerId);

        objectStorage.delete(file.getFilStorageKey());
        fileLedger.discard(file);
        // The quota goes back to the file's owner, who is not necessarily the caller (files:manage-all).
        userSummaryCacheEvictor.evict(file.getUsrId());
    }
}
