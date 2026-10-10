package com.filevault.filevaultserver.action.file;

import com.filevault.filevaultserver.exception.file.StoredFileNotFoundException;
import com.filevault.filevaultserver.models.StoredFile;
import com.filevault.filevaultserver.models.StoredFileStatus;
import com.filevault.filevaultserver.policy.FilePolicy;
import com.filevault.filevaultserver.repository.file.StoredFileRepository;
import com.filevault.filevaultserver.response.file.FileResponse;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GetFileAction {

    private final StoredFileRepository storedFileRepository;
    private final FilePolicy filePolicy;

    public GetFileAction(StoredFileRepository storedFileRepository, FilePolicy filePolicy) {
        this.storedFileRepository = storedFileRepository;
        this.filePolicy = filePolicy;
    }

    @Transactional(readOnly = true)
    public FileResponse execute(UUID callerId, UUID filId) {
        StoredFile file = storedFileRepository.getOrThrow(filId);
        filePolicy.checkCanAccess(file, callerId);
        if (file.getFilStatus() != StoredFileStatus.READY) {
            throw new StoredFileNotFoundException(filId);
        }
        return FileResponse.from(file);
    }
}
