package com.filevault.filevaultserver.action.role;

import com.filevault.filevaultserver.exception.RoleAlreadyExistsException;
import com.filevault.filevaultserver.models.Role;
import com.filevault.filevaultserver.repository.PermissionRepository;
import com.filevault.filevaultserver.repository.RoleRepository;
import com.filevault.filevaultserver.request.role.UpdateRoleRequest;
import com.filevault.filevaultserver.response.role.RoleResponse;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UpdateRoleAction {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public UpdateRoleAction(RoleRepository roleRepository, PermissionRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    @Transactional
    public RoleResponse execute(Long roleId, UpdateRoleRequest request) {
        Role role = roleRepository.getOrThrow(roleId);
        roleRepository.findByRolName(request.name())
                .filter(existing -> !existing.getRolId().equals(roleId))
                .ifPresent(existing -> {
                    throw new RoleAlreadyExistsException(request.name());
                });

        String previousName = role.getRolName();
        role.setRolName(request.name());
        role.setRolStorageQuotaBytes(request.storageQuotaBytes());

        if (!previousName.equals(request.name())) {
            renameDynamicPermission(previousName, request.name());
        }
        return RoleResponse.from(role);
    }

    private void renameDynamicPermission(String previousRoleName, String newRoleName) {
        permissionRepository
                .findByPrmName("roles:give:" + previousRoleName)
                .ifPresent(permission -> permission.setPrmName("roles:give:" + newRoleName));
    }
}
