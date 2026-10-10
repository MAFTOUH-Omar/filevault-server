package com.filevault.filevaultserver.exception.file;

public class StorageUnavailableException extends RuntimeException {

    public StorageUnavailableException() {
        super("File storage is temporarily unavailable, please try again later");
    }
}
