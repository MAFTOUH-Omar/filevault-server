package com.filevault.filevaultserver.action.role;

import com.filevault.filevaultserver.models.Role;
import com.filevault.filevaultserver.repository.role.RoleRepository;
import com.filevault.filevaultserver.response.PageResponse;
import com.filevault.filevaultserver.response.role.RoleResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ListRolesAction {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final RoleRepository roleRepository;

    public ListRolesAction(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<RoleResponse> execute(String search, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by("rolName"));
        Page<Role> result = (search == null || search.isBlank())
                ? roleRepository.findAll(pageable)
                : roleRepository.searchByName(escapeLike(search.trim()), pageable);
        return PageResponse.from(result, RoleResponse::from);
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /**
     * '%' and '_' are LIKE wildcards in Postgres; without escaping them, a search for a literal "%"
     * (or "_") would instead match anything, and a search string composed mostly of wildcards could
     * degrade into an expensive, near-unanchored scan. Escaping the backslash first is essential —
     * otherwise an input that already contains '\%' would be double-unescaped by the ESCAPE clause.
     */
    private String escapeLike(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
