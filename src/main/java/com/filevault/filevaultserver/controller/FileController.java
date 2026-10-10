package com.filevault.filevaultserver.controller;

import com.filevault.filevaultserver.action.file.CompleteUploadAction;
import com.filevault.filevaultserver.action.file.CreateDownloadLinkAction;
import com.filevault.filevaultserver.action.file.DeleteFileAction;
import com.filevault.filevaultserver.action.file.GetFileAction;
import com.filevault.filevaultserver.action.file.InitiateUploadAction;
import com.filevault.filevaultserver.action.file.ListFilesAction;
import com.filevault.filevaultserver.middleware.ErrorResponse;
import com.filevault.filevaultserver.request.file.InitiateUploadRequest;
import com.filevault.filevaultserver.response.PageResponse;
import com.filevault.filevaultserver.response.file.DownloadLinkResponse;
import com.filevault.filevaultserver.response.file.FileResponse;
import com.filevault.filevaultserver.response.file.UploadTicketResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
@Tag(
        name = "Files",
        description = "File storage. Bytes go straight between the client and the object store through "
                + "short-lived presigned URLs; this API only authorizes, tracks quota and signs.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(
            responseCode = "401",
            description = "Missing, invalid, or expired access token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
            responseCode = "429",
            description = "Too many requests",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class FileController {

    private final InitiateUploadAction initiateUploadAction;
    private final CompleteUploadAction completeUploadAction;
    private final ListFilesAction listFilesAction;
    private final GetFileAction getFileAction;
    private final CreateDownloadLinkAction createDownloadLinkAction;
    private final DeleteFileAction deleteFileAction;

    public FileController(
            InitiateUploadAction initiateUploadAction,
            CompleteUploadAction completeUploadAction,
            ListFilesAction listFilesAction,
            GetFileAction getFileAction,
            CreateDownloadLinkAction createDownloadLinkAction,
            DeleteFileAction deleteFileAction) {
        this.initiateUploadAction = initiateUploadAction;
        this.completeUploadAction = completeUploadAction;
        this.listFilesAction = listFilesAction;
        this.getFileAction = getFileAction;
        this.createDownloadLinkAction = createDownloadLinkAction;
        this.deleteFileAction = deleteFileAction;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('files:upload')")
    @Operation(
            summary = "Start an upload",
            description = "Reserves quota for the declared size and returns a presigned URL. The client must "
                    + "then PUT exactly `sizeBytes` raw bytes to `uploadUrl`, sending every header in "
                    + "`uploadHeaders` unchanged (notably Content-Type), and finally call "
                    + "`POST /api/files/{id}/complete`. Uploads not completed in time are discarded and "
                    + "their quota is released.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Upload reserved, presigned URL issued"),
        @ApiResponse(
                responseCode = "400",
                description = "Validation error (file name, content type or size)",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Caller lacks the 'files:upload' permission",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "The user's storage quota cannot hold this file",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "413",
                description = "The file exceeds the maximum allowed size",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UploadTicketResponse> initiate(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody InitiateUploadRequest request) {
        UploadTicketResponse ticket = initiateUploadAction.execute(UUID.fromString(jwt.getSubject()), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ticket);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('files:upload')")
    @Operation(
            summary = "Confirm an upload",
            description = "Checks that the object store really holds the declared number of bytes, then makes "
                    + "the file available. Idempotent for an already confirmed file. A size mismatch discards "
                    + "the upload and releases its quota.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "File confirmed"),
        @ApiResponse(
                responseCode = "403",
                description = "Caller lacks the 'files:upload' permission",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "No such file, it belongs to someone else, or the pending upload expired",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "The bytes have not reached the object store yet",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "The stored size differs from the declared size; the upload was discarded",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "503",
                description = "The object store is temporarily unavailable",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public FileResponse complete(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        return completeUploadAction.execute(UUID.fromString(jwt.getSubject()), id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('files:read')")
    @Operation(
            summary = "List my files, newest first",
            description = "Only confirmed uploads are listed. 'size' is capped at 100.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Files listed"),
        @ApiResponse(
                responseCode = "403",
                description = "Caller lacks the 'files:read' permission",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public PageResponse<FileResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, capped at 100") @RequestParam(defaultValue = "20") int size) {
        return listFilesAction.execute(UUID.fromString(jwt.getSubject()), page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('files:read')")
    @Operation(summary = "Get a file's metadata")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "File found"),
        @ApiResponse(
                responseCode = "403",
                description = "Caller lacks the 'files:read' permission",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "No such file, or it belongs to someone else (unless the caller holds 'files:manage-all')",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public FileResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        return getFileAction.execute(UUID.fromString(jwt.getSubject()), id);
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('files:read')")
    @Operation(
            summary = "Get a short-lived download link",
            description = "Returns a presigned URL to GET the file directly from the object store, served as an "
                    + "attachment under its original name. The link expires quickly; ask for a new one when needed.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Download link issued"),
        @ApiResponse(
                responseCode = "403",
                description = "Caller lacks the 'files:read' permission",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "No such file, or it belongs to someone else (unless the caller holds 'files:manage-all')",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public DownloadLinkResponse download(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        return createDownloadLinkAction.execute(UUID.fromString(jwt.getSubject()), id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('files:delete')")
    @Operation(
            summary = "Delete a file",
            description = "Removes the stored object and gives its size back to the owner's quota.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "File deleted"),
        @ApiResponse(
                responseCode = "403",
                description = "Caller lacks the 'files:delete' permission",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "No such file, or it belongs to someone else (unless the caller holds 'files:manage-all')",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "503",
                description = "The object store is temporarily unavailable; nothing was changed, retry later",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        deleteFileAction.execute(UUID.fromString(jwt.getSubject()), id);
        return ResponseEntity.noContent().build();
    }
}
