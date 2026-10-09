package com.filevault.filevaultserver.action.role;

import com.filevault.filevaultserver.models.Role;
import com.filevault.filevaultserver.repository.PermissionRepository;
import com.filevault.filevaultserver.repository.RoleRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DeleteRoleAction {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public DeleteRoleAction(RoleRepository roleRepository, PermissionRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    @Transactional
    public void execute(Long roleId) {
        Role role = roleRepository.getOrThrow(roleId);
        permissionRepository.findByPrmName("roles:give:" + role.getRolName()).ifPresent(permissionRepository::delete);
        roleRepository.delete(role);
    }
}
