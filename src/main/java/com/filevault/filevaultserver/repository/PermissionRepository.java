package com.filevault.filevaultserver.repository;

import com.filevault.filevaultserver.models.Permission;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByPrmName(String prmName);
}
