package com.filevault.filevaultserver.action.role;

import com.filevault.filevaultserver.exception.role.RoleAlreadyExistsException;
import com.filevault.filevaultserver.models.Role;
import com.filevault.filevaultserver.repository.role.PermissionRepository;
import com.filevault.filevaultserver.repository.role.RoleRepository;
import com.filevault.filevaultserver.repository.user.UserRepository;
import com.filevault.filevaultserver.request.role.UpdateRoleRequest;
import com.filevault.filevaultserver.response.role.RoleResponse;
import com.filevault.filevaultserver.security.UserSummaryCacheEvictor;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UpdateRoleAction {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    private final UserRepository userRepository;
    private final UserSummaryCacheEvictor userSummaryCacheEvictor;

    public UpdateRoleAction(
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            UserRepository userRepository,
            UserSummaryCacheEvictor userSummaryCacheEvictor) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.userSummaryCacheEvictor = userSummaryCacheEvictor;
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
        Long previousQuotaBytes = role.getRolStorageQuotaBytes();
        role.setRolName(request.name());
        role.setRolStorageQuotaBytes(request.storageQuotaBytes());

        if (!previousName.equals(request.name())) {
            renameDynamicPermission(previousName, request.name());
        }
        if (!Objects.equals(previousQuotaBytes, request.storageQuotaBytes())) {
            // /auth/me exposes the quota (and what remains of it): holders of this role must not see the old one.
            userRepository.findByRoles_RolId(roleId)
                    .forEach(user -> userSummaryCacheEvictor.evict(user.getUsrId()));
        }
        return RoleResponse.from(role);
    }

    private void renameDynamicPermission(String previousRoleName, String newRoleName) {
        permissionRepository.findByPrmName("roles:give:" + previousRoleName).ifPresent(permission -> {
            permission.setPrmName("roles:give:" + newRoleName);
            permission.setPrmDescription("Can grant the role " + newRoleName);
        });
    }
}
