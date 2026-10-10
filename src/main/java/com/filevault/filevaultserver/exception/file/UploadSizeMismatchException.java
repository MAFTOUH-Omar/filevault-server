package com.filevault.filevaultserver.exception.file;

public class UploadSizeMismatchException extends RuntimeException {

    public UploadSizeMismatchException() {
        super("The uploaded file size does not match the declared size; the upload was discarded");
    }
}
