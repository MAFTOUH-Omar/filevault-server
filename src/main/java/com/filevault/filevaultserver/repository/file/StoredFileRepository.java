package com.filevault.filevaultserver.repository.file;

import com.filevault.filevaultserver.exception.file.StoredFileNotFoundException;
import com.filevault.filevaultserver.models.StoredFile;
import com.filevault.filevaultserver.models.StoredFileStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

    Page<StoredFile> findByUsrIdAndFilStatus(UUID usrId, StoredFileStatus filStatus, Pageable pageable);

    /** Backed by the partial index on PENDING rows (idx_fil_stored_files_pending_created_at). */
    List<StoredFile> findByFilStatusAndFilCreatedAtBefore(StoredFileStatus filStatus, Instant cutoff, Pageable pageable);

    /**
     * Status transitions are single conditional statements, not read-modify-write on an entity: the
     * upload confirmation and the expired-upload sweeper can race on the same row, and whichever
     * statement runs first must be the only one that takes effect.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update StoredFile f set f.filStatus = :to where f.filId = :filId and f.filStatus = :from")
    int updateStatus(
            @Param("filId") UUID filId, @Param("from") StoredFileStatus from, @Param("to") StoredFileStatus to);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from StoredFile f where f.filId = :filId")
    int deleteRow(@Param("filId") UUID filId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from StoredFile f where f.filId = :filId and f.filStatus = :status")
    int deleteRowIfStatus(@Param("filId") UUID filId, @Param("status") StoredFileStatus status);

    default StoredFile getOrThrow(UUID filId) {
        return findById(filId).orElseThrow(() -> new StoredFileNotFoundException(filId));
    }
}
