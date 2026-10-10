package com.filevault.filevaultserver.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fil_stored_files")
public class StoredFile {

    @Id
    @Column(name = "fil_id")
    private UUID filId;

    @Column(name = "usr_id", nullable = false, updatable = false)
    private UUID usrId;

    @Column(name = "fil_original_name", nullable = false)
    private String filOriginalName;

    /** Object key in the bucket: "<usrId>/<filId>". Never derived from the client-supplied name. */
    @Column(name = "fil_storage_key", nullable = false, unique = true, updatable = false)
    private String filStorageKey;

    @Column(name = "fil_content_type", nullable = false)
    private String filContentType;

    @Column(name = "fil_size_bytes", nullable = false)
    private long filSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "fil_status", nullable = false)
    private StoredFileStatus filStatus;

    @Column(name = "fil_created_at", nullable = false)
    private Instant filCreatedAt;

    protected StoredFile() {
    }

    /** A new file always starts PENDING: its upload has not been confirmed yet. */
    public StoredFile(UUID usrId, String filOriginalName, String filContentType, long filSizeBytes) {
        this.filId = UUID.randomUUID();
        this.usrId = usrId;
        this.filOriginalName = filOriginalName;
        this.filStorageKey = usrId + "/" + filId;
        this.filContentType = filContentType;
        this.filSizeBytes = filSizeBytes;
        this.filStatus = StoredFileStatus.PENDING;
        this.filCreatedAt = Instant.now();
    }

    public UUID getFilId() {
        return filId;
    }

    public UUID getUsrId() {
        return usrId;
    }

    public String getFilOriginalName() {
        return filOriginalName;
    }

    public String getFilStorageKey() {
        return filStorageKey;
    }

    public String getFilContentType() {
        return filContentType;
    }

    public long getFilSizeBytes() {
        return filSizeBytes;
    }

    public StoredFileStatus getFilStatus() {
        return filStatus;
    }

    public Instant getFilCreatedAt() {
        return filCreatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StoredFile other)) {
            return false;
        }
        return filId != null && filId.equals(other.filId);
    }

    @Override
    public int hashCode() {
        return filId == null ? 0 : filId.hashCode();
    }
}
