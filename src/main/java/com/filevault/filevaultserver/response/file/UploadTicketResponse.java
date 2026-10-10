package com.filevault.filevaultserver.response.file;

import java.time.Instant;
import java.util.Map;

/**
 * Everything a client needs to send the bytes straight to the object store: PUT the raw body to
 * {@code uploadUrl} with exactly these {@code uploadHeaders}, then confirm via the "complete" endpoint.
 * {@code uploadUrl} is a credential — treat it like a password.
 */
public record UploadTicketResponse(
        FileResponse file, String uploadMethod, String uploadUrl, Map<String, String> uploadHeaders, Instant expiresAt) {
}
