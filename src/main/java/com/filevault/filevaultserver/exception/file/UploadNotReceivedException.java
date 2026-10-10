package com.filevault.filevaultserver.exception.file;

public class UploadNotReceivedException extends RuntimeException {

    public UploadNotReceivedException() {
        super("The file has not been uploaded yet");
    }
}
