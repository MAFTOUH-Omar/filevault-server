package com.filevault.filevaultserver.exception.file;

import java.util.UUID;

public class StoredFileNotFoundException extends RuntimeException {

    public StoredFileNotFoundException(UUID filId) {
        super("File not found: " + filId);
    }
}
