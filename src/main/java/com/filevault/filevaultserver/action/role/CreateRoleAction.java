package com.filevault.filevaultserver.action.role;

import com.filevault.filevaultserver.exception.RoleAlreadyExistsException;
import com.filevault.filevaultserver.models.Permission;
import com.filevault.filevaultserver.models.Role;
import com.filevault.filevaultserver.repository.PermissionRepository;
import com.filevault.filevaultserver.repository.RoleRepository;
import com.filevault.filevaultserver.request.role.CreateRoleRequest;
import com.filevault.filevaultserver.response.role.RoleResponse;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating a role also dynamically creates its matching "roles:give:&lt;name&gt;" permission, and
 * grants that permission to every role that already holds roles:manage — so the platform's
 * super-admins automatically gain the ability to assign a brand-new role without a manual seed
 * step, while everyone else stays unable to grant it until explicitly authorized.
 */
@Component
public class CreateRoleAction {

    private static final String GRANT_ALL_PERMISSION = "roles:manage";

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public CreateRoleAction(RoleRepository roleRepository, PermissionRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    @Transactional
    public RoleResponse execute(CreateRoleRequest request) {
        if (roleRepository.existsByRolName(request.name())) {
            throw new RoleAlreadyExistsException(request.name());
        }
        Role role = new Role(request.name(), request.storageQuotaBytes());
        roleRepository.save(role);
        grantDynamicPermission(role);
        return RoleResponse.from(role);
    }

    private void grantDynamicPermission(Role role) {
        Permission givePermission = permissionRepository.save(new Permission(
                "roles:give:" + role.getRolName(), "Peut attribuer le rôle " + role.getRolName()));
        roleRepository.findByPermissions_PrmName(GRANT_ALL_PERMISSION)
                .forEach(manager -> manager.getPermissions().add(givePermission));
    }
}
