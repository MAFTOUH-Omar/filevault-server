package com.filevault.filevaultserver.models;

/**
 * PENDING: the row and its quota are reserved and an upload URL was issued, but the bytes have not
 * been confirmed in the object store yet. READY: confirmed, visible, downloadable.
 */
public enum StoredFileStatus {
    PENDING,
    READY
}
