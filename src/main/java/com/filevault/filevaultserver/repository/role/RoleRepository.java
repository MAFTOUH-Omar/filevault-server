package com.filevault.filevaultserver.repository.role;

import com.filevault.filevaultserver.exception.role.RoleNotFoundException;
import com.filevault.filevaultserver.models.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByRolName(String rolName);

    boolean existsByRolName(String rolName);

    List<Role> findByPermissions_PrmName(String prmName);

    default Role getOrThrow(Long rolId) {
        return findById(rolId).orElseThrow(() -> new RoleNotFoundException(rolId));
    }
}
