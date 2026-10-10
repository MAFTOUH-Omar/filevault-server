package com.filevault.filevaultserver.exception.file;

public class StorageQuotaExceededException extends RuntimeException {

    public StorageQuotaExceededException() {
        super("Storage quota exceeded");
    }
}
