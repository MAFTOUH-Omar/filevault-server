package com.filevault.filevaultserver.action.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.filevault.filevaultserver.exception.file.FileTooLargeException;
import com.filevault.filevaultserver.exception.file.StorageQuotaExceededException;
import com.filevault.filevaultserver.exception.file.StorageUnavailableException;
import com.filevault.filevaultserver.exception.file.StoredFileNotFoundException;
import com.filevault.filevaultserver.exception.file.UploadNotReceivedException;
import com.filevault.filevaultserver.exception.file.UploadSizeMismatchException;
import com.filevault.filevaultserver.models.StoredFile;
import com.filevault.filevaultserver.models.StoredFileStatus;
import com.filevault.filevaultserver.policy.FilePolicy;
import com.filevault.filevaultserver.repository.file.StoredFileRepository;
import com.filevault.filevaultserver.request.file.InitiateUploadRequest;
import com.filevault.filevaultserver.response.file.FileResponse;
import com.filevault.filevaultserver.response.file.UploadTicketResponse;
import com.filevault.filevaultserver.security.UserSummaryCacheEvictor;
import com.filevault.filevaultserver.storage.ObjectStorage;
import com.filevault.filevaultserver.storage.StorageProperties;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class UploadActionsTest {

    private static final long MAX_FILE_BYTES = 1_000;

    private final UUID owner = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();

    private StoredFileRepository repository;
    private FileLedger ledger;
    private FilePolicy policy;
    private ObjectStorage storage;
    private UserSummaryCacheEvictor evictor;
    private StorageProperties properties;

    @BeforeEach
    void setUp() {
        repository = mock(StoredFileRepository.class);
        ledger = mock(FileLedger.class);
        policy = new FilePolicy();
        storage = mock(ObjectStorage.class);
        evictor = mock(UserSummaryCacheEvictor.class);
        properties = new StorageProperties(
                new StorageProperties.R2("acct", "key", "secret", "bucket", null),
                MAX_FILE_BYTES,
                Duration.ofMinutes(15),
                Duration.ofMinutes(5),
                Duration.ofMinutes(30),
                600L);
    }

    private StoredFile pendingFile(long sizeBytes) {
        return new StoredFile(owner, "report.pdf", "application/pdf", sizeBytes);
    }

    private StoredFile readyFile(long sizeBytes) {
        StoredFile file = pendingFile(sizeBytes);
        ReflectionTestUtils.setField(file, "filStatus", StoredFileStatus.READY);
        return file;
    }

    // ---- InitiateUploadAction ----

    private InitiateUploadAction initiate() {
        return new InitiateUploadAction(ledger, storage, properties, evictor);
    }

    @Test
    void initiateRefusesAFileOverTheLimitWithoutTouchingQuota() {
        var request = new InitiateUploadRequest("big.bin", "application/octet-stream", MAX_FILE_BYTES + 1);

        assertThatThrownBy(() -> initiate().execute(owner, request)).isInstanceOf(FileTooLargeException.class);
        verify(ledger, never()).reserve(any(), anyString(), anyString(), anyLong());
    }

    @Test
    void initiatePropagatesAQuotaRefusalAndSignsNothing() {
        when(ledger.reserve(any(), anyString(), anyString(), anyLong())).thenThrow(new StorageQuotaExceededException());
        var request = new InitiateUploadRequest("a.txt", "text/plain", 10L);

        assertThatThrownBy(() -> initiate().execute(owner, request)).isInstanceOf(StorageQuotaExceededException.class);
        verify(storage, never()).presignUpload(anyString(), anyString(), anyLong());
    }

    @Test
    void initiateReservesNormalizedMetadataThenReturnsTheSignedTicket() {
        StoredFile file = pendingFile(10);
        when(ledger.reserve(owner, "a.txt", "text/plain", 10L)).thenReturn(file);
        Instant expiresAt = Instant.now().plusSeconds(900);
        when(storage.presignUpload(file.getFilStorageKey(), "application/pdf", 10L))
                .thenReturn(new ObjectStorage.PresignedUpload(
                        URI.create("https://r2.example/bucket/key?sig=1"), Map.of("content-type", "application/pdf"), expiresAt));

        UploadTicketResponse ticket =
                initiate().execute(owner, new InitiateUploadRequest("  a.txt ", "Text/Plain", 10L));

        assertThat(ticket.uploadMethod()).isEqualTo("PUT");
        assertThat(ticket.uploadUrl()).isEqualTo("https://r2.example/bucket/key?sig=1");
        assertThat(ticket.uploadHeaders()).containsEntry("content-type", "application/pdf");
        assertThat(ticket.file().status()).isEqualTo("PENDING");
        verify(evictor).evict(owner);
    }

    @Test
    void initiateReleasesTheReservationIfSigningFails() {
        StoredFile file = pendingFile(10);
        when(ledger.reserve(any(), anyString(), anyString(), anyLong())).thenReturn(file);
        when(storage.presignUpload(anyString(), anyString(), anyLong())).thenThrow(new StorageUnavailableException());

        assertThatThrownBy(() -> initiate().execute(owner, new InitiateUploadRequest("a.txt", "text/plain", 10L)))
                .isInstanceOf(StorageUnavailableException.class);
        verify(ledger).discardPending(file);
    }

    // ---- CompleteUploadAction ----

    private CompleteUploadAction complete() {
        return new CompleteUploadAction(repository, ledger, policy, storage, evictor);
    }

    @Test
    void completeConfirmsWhenTheStoredSizeMatches() {
        StoredFile pending = pendingFile(10);
        when(repository.getOrThrow(pending.getFilId())).thenReturn(pending);
        when(storage.sizeOf(pending.getFilStorageKey())).thenReturn(OptionalLong.of(10));
        when(ledger.markReady(pending.getFilId())).thenReturn(readyFile(10));

        FileResponse response = complete().execute(owner, pending.getFilId());

        assertThat(response.status()).isEqualTo("READY");
    }

    @Test
    void completeIsIdempotentOnAReadyFile() {
        StoredFile ready = readyFile(10);
        when(repository.getOrThrow(ready.getFilId())).thenReturn(ready);

        assertThat(complete().execute(owner, ready.getFilId()).status()).isEqualTo("READY");
        verify(storage, never()).sizeOf(anyString());
    }

    @Test
    void completeAsksToRetryWhenNothingWasUploadedYet() {
        StoredFile pending = pendingFile(10);
        when(repository.getOrThrow(pending.getFilId())).thenReturn(pending);
        when(storage.sizeOf(anyString())).thenReturn(OptionalLong.empty());

        assertThatThrownBy(() -> complete().execute(owner, pending.getFilId()))
                .isInstanceOf(UploadNotReceivedException.class);
        verify(ledger, never()).discardPending(any());
    }

    @Test
    void completeDiscardsAnUploadWhoseSizeDiffers() {
        StoredFile pending = pendingFile(10);
        when(repository.getOrThrow(pending.getFilId())).thenReturn(pending);
        when(storage.sizeOf(pending.getFilStorageKey())).thenReturn(OptionalLong.of(11));
        when(ledger.discardPending(pending)).thenReturn(true);

        assertThatThrownBy(() -> complete().execute(owner, pending.getFilId()))
                .isInstanceOf(UploadSizeMismatchException.class);
        verify(storage).deleteQuietly(pending.getFilStorageKey());
        verify(evictor).evict(owner);
        verify(ledger, never()).markReady(any());
    }

    @Test
    void completeLeavesTheObjectAloneWhenSomeoneElseAlreadyConfirmedIt() {
        // discardPending() == false means the row is no longer PENDING: the object now belongs to a READY file.
        StoredFile pending = pendingFile(10);
        when(repository.getOrThrow(pending.getFilId())).thenReturn(pending);
        when(storage.sizeOf(anyString())).thenReturn(OptionalLong.of(11));
        when(ledger.discardPending(pending)).thenReturn(false);

        assertThatThrownBy(() -> complete().execute(owner, pending.getFilId()))
                .isInstanceOf(UploadSizeMismatchException.class);
        verify(storage, never()).deleteQuietly(anyString());
    }

    @Test
    void completeHidesSomeoneElsesFileAsNotFound() {
        StoredFile pending = pendingFile(10);
        when(repository.getOrThrow(pending.getFilId())).thenReturn(pending);

        assertThatThrownBy(() -> complete().execute(stranger, pending.getFilId()))
                .isInstanceOf(StoredFileNotFoundException.class);
        verify(storage, never()).sizeOf(anyString());
    }

    // ---- DeleteFileAction ----

    @Test
    void deleteRemovesTheObjectBeforeTheRowAndEvictsTheOwner() {
        StoredFile file = readyFile(10);
        when(repository.getOrThrow(file.getFilId())).thenReturn(file);
        var inOrder = org.mockito.Mockito.inOrder(storage, ledger);

        new DeleteFileAction(repository, ledger, policy, storage, evictor).execute(owner, file.getFilId());

        inOrder.verify(storage).delete(file.getFilStorageKey());
        inOrder.verify(ledger).discard(file);
        verify(evictor).evict(owner);
    }

    @Test
    void deleteKeepsTheRowWhenTheObjectStoreIsDown() {
        StoredFile file = readyFile(10);
        when(repository.getOrThrow(file.getFilId())).thenReturn(file);
        org.mockito.Mockito.doThrow(new StorageUnavailableException()).when(storage).delete(anyString());

        assertThatThrownBy(() -> new DeleteFileAction(repository, ledger, policy, storage, evictor)
                        .execute(owner, file.getFilId()))
                .isInstanceOf(StorageUnavailableException.class);
        verify(ledger, never()).discard(any());
    }

    // ---- PurgeExpiredUploadsAction ----

    @Test
    void purgeOnlyDeletesObjectsOfUploadsItActuallyClaimed() {
        StoredFile claimed = pendingFile(10);
        StoredFile confirmedMeanwhile = pendingFile(20);
        when(repository.findByFilStatusAndFilCreatedAtBefore(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(claimed, confirmedMeanwhile));
        when(ledger.discardPending(claimed)).thenReturn(true);
        when(ledger.discardPending(confirmedMeanwhile)).thenReturn(false);

        int purged = new PurgeExpiredUploadsAction(repository, ledger, storage, evictor, properties).execute();

        assertThat(purged).isEqualTo(1);
        verify(storage).deleteQuietly(claimed.getFilStorageKey());
        verify(storage, never()).deleteQuietly(confirmedMeanwhile.getFilStorageKey());
        verify(evictor).evict(owner);
    }

    @Test
    void purgeKeepsGoingAfterOneFileFails() {
        StoredFile broken = pendingFile(10);
        StoredFile fine = pendingFile(20);
        when(repository.findByFilStatusAndFilCreatedAtBefore(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(broken, fine));
        when(ledger.discardPending(broken)).thenThrow(new IllegalStateException("db hiccup"));
        when(ledger.discardPending(fine)).thenReturn(true);

        assertThat(new PurgeExpiredUploadsAction(repository, ledger, storage, evictor, properties).execute())
                .isEqualTo(1);
    }
}
