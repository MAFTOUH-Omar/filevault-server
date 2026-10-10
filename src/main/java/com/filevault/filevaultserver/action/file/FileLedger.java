package com.filevault.filevaultserver.action.file;

import com.filevault.filevaultserver.exception.file.StorageQuotaExceededException;
import com.filevault.filevaultserver.exception.file.StoredFileNotFoundException;
import com.filevault.filevaultserver.models.StoredFile;
import com.filevault.filevaultserver.models.StoredFileStatus;
import com.filevault.filevaultserver.repository.file.StoredFileRepository;
import com.filevault.filevaultserver.repository.user.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The database half of every file operation, each method one short transaction: a file row and the
 * owner's {@code usr_storage_used_bytes} counter always change together, so they can never disagree.
 * Object-store calls deliberately happen outside these transactions (in the actions), so a slow or
 * failing R2 never holds a database connection or rolls back bookkeeping that is already correct.
 */
@Component
class FileLedger {

    private final StoredFileRepository storedFileRepository;
    private final UserRepository userRepository;

    FileLedger(StoredFileRepository storedFileRepository, UserRepository userRepository) {
        this.storedFileRepository = storedFileRepository;
        this.userRepository = userRepository;
    }

    /** Reserves quota and records a PENDING file; throws if the owner's quota cannot hold {@code sizeBytes} more. */
    @Transactional
    public StoredFile reserve(UUID usrId, String originalName, String contentType, long sizeBytes) {
        if (userRepository.reserveStorage(usrId, sizeBytes) == 0) {
            throw new StorageQuotaExceededException();
        }
        return storedFileRepository.save(new StoredFile(usrId, originalName, contentType, sizeBytes));
    }

    /** PENDING -> READY, atomically; fails if the sweeper (or a delete) got to the row first. */
    @Transactional
    public StoredFile markReady(UUID filId) {
        if (storedFileRepository.updateStatus(filId, StoredFileStatus.PENDING, StoredFileStatus.READY) == 0) {
            throw new StoredFileNotFoundException(filId);
        }
        return storedFileRepository.getOrThrow(filId);
    }

    /** Removes the file whatever its status and gives its quota back. Releases only if a row was really removed. */
    @Transactional
    public boolean discard(StoredFile file) {
        if (storedFileRepository.deleteRow(file.getFilId()) == 0) {
            return false;
        }
        userRepository.releaseStorage(file.getUsrId(), file.getFilSizeBytes());
        return true;
    }

    /**
     * Same, but only if the file is still PENDING. This is the claim the expired-upload sweeper and the
     * size-mismatch cleanup use: if the upload was confirmed in the meantime the row is READY, nothing
     * is removed, and the caller must leave the object alone.
     */
    @Transactional
    public boolean discardPending(StoredFile file) {
        if (storedFileRepository.deleteRowIfStatus(file.getFilId(), StoredFileStatus.PENDING) == 0) {
            return false;
        }
        userRepository.releaseStorage(file.getUsrId(), file.getFilSizeBytes());
        return true;
    }
}
