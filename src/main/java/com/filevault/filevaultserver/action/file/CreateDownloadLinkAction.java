package com.filevault.filevaultserver.action.file;

import com.filevault.filevaultserver.exception.file.StoredFileNotFoundException;
import com.filevault.filevaultserver.models.StoredFile;
import com.filevault.filevaultserver.models.StoredFileStatus;
import com.filevault.filevaultserver.policy.FilePolicy;
import com.filevault.filevaultserver.repository.file.StoredFileRepository;
import com.filevault.filevaultserver.response.file.DownloadLinkResponse;
import com.filevault.filevaultserver.storage.ObjectStorage;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Authorizes against the database, then signs a short-lived link: the bytes never touch this server. */
@Component
public class CreateDownloadLinkAction {

    private final StoredFileRepository storedFileRepository;
    private final FilePolicy filePolicy;
    private final ObjectStorage objectStorage;

    public CreateDownloadLinkAction(
            StoredFileRepository storedFileRepository, FilePolicy filePolicy, ObjectStorage objectStorage) {
        this.storedFileRepository = storedFileRepository;
        this.filePolicy = filePolicy;
        this.objectStorage = objectStorage;
    }

    public DownloadLinkResponse execute(UUID callerId, UUID filId) {
        StoredFile file = storedFileRepository.getOrThrow(filId);
        filePolicy.checkCanAccess(file, callerId);
        if (file.getFilStatus() != StoredFileStatus.READY) {
            throw new StoredFileNotFoundException(filId);
        }
        ObjectStorage.PresignedDownload download =
                objectStorage.presignDownload(file.getFilStorageKey(), file.getFilOriginalName());
        return new DownloadLinkResponse(download.url().toString(), file.getFilOriginalName(), download.expiresAt());
    }
}
