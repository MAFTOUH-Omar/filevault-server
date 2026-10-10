package com.filevault.filevaultserver.action.role;

import com.filevault.filevaultserver.models.Permission;
import com.filevault.filevaultserver.models.Role;
import com.filevault.filevaultserver.models.User;
import com.filevault.filevaultserver.repository.role.PermissionRepository;
import com.filevault.filevaultserver.repository.role.RoleRepository;
import com.filevault.filevaultserver.repository.user.UserRepository;
import com.filevault.filevaultserver.security.UserSummaryCacheEvictor;
import org.springframework.stereotype.Component;

/**
 * Shared by CreateRoleAction (a brand-new role from the API) and DeleteRoleAction (an "archive-X"
 * role created on demand when force-deleting a role that still has users) — both need the exact
 * same "new role + its dynamic roles:give:&lt;name&gt; permission, granted to every roles:manage
 * holder" sequence. Callers are responsible for checking the name doesn't already exist first; this
 * always creates.
 */
@Component
class RoleProvisioner {

    private static final String GRANT_ALL_PERMISSION = "roles:manage";

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final UserSummaryCacheEvictor cacheEvictor;

    RoleProvisioner(
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            UserRepository userRepository,
            UserSummaryCacheEvictor cacheEvictor) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.cacheEvictor = cacheEvictor;
    }

    Role createRoleWithGrantPermission(String rolName, Long storageQuotaBytes) {
        Role role = new Role(rolName, storageQuotaBytes);
        roleRepository.save(role);

        // saveAndFlush, not save: this is an IDENTITY-generated entity, and when more entities are
        // already dirty in the same persistence context (e.g. DeleteRoleAction archiving users in
        // the same transaction this runs inside), Hibernate can defer/reorder the INSERT past the
        // join-table row that references it, raising TransientPropertyValueException at flush time.
        // Flushing here immediately guarantees the row exists before anything references its id.
        Permission givePermission = permissionRepository.saveAndFlush(
                new Permission("roles:give:" + rolName, "Can grant the role " + rolName));
        for (Role manager : roleRepository.findByPermissions_PrmName(GRANT_ALL_PERMISSION)) {
            manager.getPermissions().add(givePermission);
            for (User user : userRepository.findByRoles_RolId(manager.getRolId())) {
                cacheEvictor.evict(user.getUsrId());
            }
        }
        return role;
    }
}
