package com.filevault.filevaultserver.response.file;

import java.time.Instant;

/** A short-lived link to fetch the file directly from the object store; {@code downloadUrl} is a credential. */
public record DownloadLinkResponse(String downloadUrl, String fileName, Instant expiresAt) {
}
