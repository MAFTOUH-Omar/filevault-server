package com.filevault.filevaultserver.action.file;

import com.filevault.filevaultserver.models.StoredFile;
import com.filevault.filevaultserver.models.StoredFileStatus;
import com.filevault.filevaultserver.repository.file.StoredFileRepository;
import com.filevault.filevaultserver.response.PageResponse;
import com.filevault.filevaultserver.response.file.FileResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The caller's own confirmed files, newest first (PENDING uploads are not files yet). */
@Component
public class ListFilesAction {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final StoredFileRepository storedFileRepository;

    public ListFilesAction(StoredFileRepository storedFileRepository) {
        this.storedFileRepository = storedFileRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<FileResponse> execute(UUID usrId, int page, int size) {
        int pageSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), pageSize, Sort.by(Sort.Direction.DESC, "filCreatedAt"));
        Page<StoredFile> result = storedFileRepository.findByUsrIdAndFilStatus(usrId, StoredFileStatus.READY, pageable);
        return PageResponse.from(result, FileResponse::from);
    }
}
