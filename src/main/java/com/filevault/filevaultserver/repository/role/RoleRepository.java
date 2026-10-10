package com.filevault.filevaultserver.repository.role;

import com.filevault.filevaultserver.exception.role.RoleNotFoundException;
import com.filevault.filevaultserver.models.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByRolName(String rolName);

    boolean existsByRolName(String rolName);

    List<Role> findByPermissions_PrmName(String prmName);

    /**
     * {@code search} must already be LIKE-escaped by the caller (see ListRolesAction) — this query
     * does not escape it, so a raw, unescaped user search term must never be passed in here.
     */
    @Query("SELECT r FROM Role r WHERE LOWER(r.rolName) LIKE LOWER(CONCAT('%', :search, '%')) ESCAPE '\\'")
    Page<Role> searchByName(@Param("search") String search, Pageable pageable);

    default Role getOrThrow(Long rolId) {
        return findById(rolId).orElseThrow(() -> new RoleNotFoundException(rolId));
    }
}
