package com.filevault.filevaultserver.exception.file;

public class FileTooLargeException extends RuntimeException {

    public FileTooLargeException(long maxSizeBytes) {
        super("File exceeds the maximum allowed size of " + maxSizeBytes + " bytes");
    }
}
