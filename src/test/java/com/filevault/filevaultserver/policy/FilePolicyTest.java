package com.filevault.filevaultserver.policy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.filevault.filevaultserver.exception.file.StoredFileNotFoundException;
import com.filevault.filevaultserver.models.StoredFile;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class FilePolicyTest {

    private final FilePolicy policy = new FilePolicy();
    private final UUID owner = UUID.randomUUID();
    private final StoredFile file = new StoredFile(owner, "a.txt", "text/plain", 1);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateWith(String... authorities) {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("caller", "n/a", authorities));
    }

    @Test
    void ownerMayAccessTheirFile() {
        authenticateWith("files:read");

        assertThatCode(() -> policy.checkCanAccess(file, owner)).doesNotThrowAnyException();
    }

    @Test
    void anotherUserGetsNotFoundNotForbidden() {
        authenticateWith("files:read");

        assertThatThrownBy(() -> policy.checkCanAccess(file, UUID.randomUUID()))
                .isInstanceOf(StoredFileNotFoundException.class);
    }

    @Test
    void manageAllHoldersMayAccessAnyFile() {
        authenticateWith("files:read", "files:manage-all");

        assertThatCode(() -> policy.checkCanAccess(file, UUID.randomUUID())).doesNotThrowAnyException();
    }

    @Test
    void anonymousContextIsRefused() {
        assertThatThrownBy(() -> policy.checkCanAccess(file, UUID.randomUUID()))
                .isInstanceOf(StoredFileNotFoundException.class);
    }
}
