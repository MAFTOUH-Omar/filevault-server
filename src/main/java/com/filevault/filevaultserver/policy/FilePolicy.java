package com.filevault.filevaultserver.policy;

import com.filevault.filevaultserver.exception.file.StoredFileNotFoundException;
import com.filevault.filevaultserver.models.StoredFile;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * A file is reachable by its owner, or by anyone holding {@code files:manage-all}. Everyone else gets
 * the same "not found" as for a file that does not exist: answering 403 would confirm that a given id
 * exists, and 404 keeps other people's files unenumerable.
 */
@Component
public class FilePolicy {

    private static final String MANAGE_ALL = "files:manage-all";

    public void checkCanAccess(StoredFile file, UUID callerId) {
        if (file.getUsrId().equals(callerId) || callerCanManageAll()) {
            return;
        }
        throw new StoredFileNotFoundException(file.getFilId());
    }

    private boolean callerCanManageAll() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(MANAGE_ALL::equals);
    }
}
