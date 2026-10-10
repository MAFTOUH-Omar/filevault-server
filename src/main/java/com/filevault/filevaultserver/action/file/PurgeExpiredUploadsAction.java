package com.filevault.filevaultserver.action.file;

import com.filevault.filevaultserver.models.StoredFile;
import com.filevault.filevaultserver.models.StoredFileStatus;
import com.filevault.filevaultserver.repository.file.StoredFileRepository;
import com.filevault.filevaultserver.security.UserSummaryCacheEvictor;
import com.filevault.filevaultserver.storage.ObjectStorage;
import com.filevault.filevaultserver.storage.StorageProperties;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Uploads that were started but never confirmed would otherwise hold their reserved quota forever.
 * Runs on a timer (like AdminBootstrapRunner, the trigger lives with the action): every PENDING file
 * older than {@code app.storage.pending-upload-ttl} is removed, its quota returned, and whatever
 * partial object exists is deleted. Each file is claimed with a status-conditional delete, so it is
 * safe if the user confirms the upload at the same moment, or if several instances run this at once.
 */
@Component
public class PurgeExpiredUploadsAction {

    private static final Logger log = LoggerFactory.getLogger(PurgeExpiredUploadsAction.class);
    private static final int BATCH_SIZE = 100;

    private final StoredFileRepository storedFileRepository;
    private final FileLedger fileLedger;
    private final ObjectStorage objectStorage;
    private final UserSummaryCacheEvictor userSummaryCacheEvictor;
    private final StorageProperties storageProperties;

    public PurgeExpiredUploadsAction(
            StoredFileRepository storedFileRepository,
            FileLedger fileLedger,
            ObjectStorage objectStorage,
            UserSummaryCacheEvictor userSummaryCacheEvictor,
            StorageProperties storageProperties) {
        this.storedFileRepository = storedFileRepository;
        this.fileLedger = fileLedger;
        this.objectStorage = objectStorage;
        this.userSummaryCacheEvictor = userSummaryCacheEvictor;
        this.storageProperties = storageProperties;
    }

    @Scheduled(
            initialDelayString = "${app.storage.cleanup-interval-seconds}",
            fixedDelayString = "${app.storage.cleanup-interval-seconds}",
            timeUnit = TimeUnit.SECONDS)
    public int execute() {
        Instant cutoff = Instant.now().minus(storageProperties.pendingUploadTtl());
        List<StoredFile> expired = storedFileRepository.findByFilStatusAndFilCreatedAtBefore(
                StoredFileStatus.PENDING, cutoff, PageRequest.of(0, BATCH_SIZE, Sort.by("filCreatedAt")));

        int purged = 0;
        for (StoredFile file : expired) {
            try {
                if (fileLedger.discardPending(file)) {
                    userSummaryCacheEvictor.evict(file.getUsrId());
                    objectStorage.deleteQuietly(file.getFilStorageKey());
                    purged++;
                }
            } catch (RuntimeException e) {
                log.warn("Could not purge expired upload {}", file.getFilId(), e);
            }
        }
        if (purged > 0) {
            log.info("Purged {} expired pending upload(s)", purged);
        }
        return purged;
    }
}
