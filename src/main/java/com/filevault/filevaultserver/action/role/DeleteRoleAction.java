package com.filevault.filevaultserver.action.role;

import com.filevault.filevaultserver.exception.role.RoleHasUsersException;
import com.filevault.filevaultserver.models.Role;
import com.filevault.filevaultserver.models.User;
import com.filevault.filevaultserver.repository.role.PermissionRepository;
import com.filevault.filevaultserver.repository.role.RoleRepository;
import com.filevault.filevaultserver.repository.user.UserRepository;
import com.filevault.filevaultserver.security.UserSummaryCacheEvictor;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * A role with users still assigned can't just be deleted — that would silently strip those users of
 * whatever that role granted them with no record of it. By default this is refused (409). Passing
 * {@code force=true} instead archives: every affected user is moved onto an "archive-&lt;name&gt;"
 * role (created on demand, reusing it if a prior deletion already made one) before the original role
 * is deleted, so there's always a trace of "this user used to have X".
 */
@Component
public class DeleteRoleAction {

    private static final String ARCHIVE_PREFIX = "archive-";

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final RoleProvisioner roleProvisioner;
    private final UserSummaryCacheEvictor cacheEvictor;

    public DeleteRoleAction(
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            UserRepository userRepository,
            RoleProvisioner roleProvisioner,
            UserSummaryCacheEvictor cacheEvictor) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.roleProvisioner = roleProvisioner;
        this.cacheEvictor = cacheEvictor;
    }

    @Transactional
    public void execute(Long roleId, boolean force) {
        Role role = roleRepository.getOrThrow(roleId);
        List<User> assignedUsers = userRepository.findByRoles_RolId(roleId);

        if (!assignedUsers.isEmpty() && !force) {
            throw new RoleHasUsersException(role.getRolName(), assignedUsers.size());
        }

        // Delete (and flush) this role's own "roles:give:<name>" permission *before* archiving: if
        // it were deleted after, any other role's permissions collection freshly loaded during
        // archiving (RoleProvisioner grants the new archive role's permission to every roles:manage
        // holder) would still hold an in-memory reference to this now-stale Permission, and
        // Hibernate's flush could try to re-sync that collection against a row that's simultaneously
        // being removed — exactly the "unsaved transient instance" class of bug. Deleting it first
        // means nothing ever loads a reference to it in this transaction.
        permissionRepository
                .findByPrmName("roles:give:" + role.getRolName())
                .ifPresent(permissionRepository::delete);
        permissionRepository.flush();

        if (!assignedUsers.isEmpty()) {
            archiveUsers(role, assignedUsers);
        }

        roleRepository.delete(role);
    }

    private void archiveUsers(Role role, List<User> users) {
        String archiveName = ARCHIVE_PREFIX + role.getRolName();
        Role archiveRole = roleRepository
                .findByRolName(archiveName)
                .orElseGet(() -> roleProvisioner.createRoleWithGrantPermission(archiveName, null));
        for (User user : users) {
            user.getRoles().remove(role);
            user.getRoles().add(archiveRole);
            cacheEvictor.evict(user.getUsrId());
        }
    }
}
