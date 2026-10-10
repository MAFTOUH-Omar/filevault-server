package com.filevault.filevaultserver.action.role;

import com.filevault.filevaultserver.models.Role;
import com.filevault.filevaultserver.models.User;
import com.filevault.filevaultserver.policy.RolePolicy;
import com.filevault.filevaultserver.repository.role.RoleRepository;
import com.filevault.filevaultserver.repository.user.UserRepository;
import com.filevault.filevaultserver.request.role.AssignRoleRequest;
import com.filevault.filevaultserver.security.UserSummaryCacheEvictor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AssignRoleAction {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final RolePolicy rolePolicy;
    private final UserSummaryCacheEvictor cacheEvictor;

    public AssignRoleAction(
            RoleRepository roleRepository,
            UserRepository userRepository,
            RolePolicy rolePolicy,
            UserSummaryCacheEvictor cacheEvictor) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.rolePolicy = rolePolicy;
        this.cacheEvictor = cacheEvictor;
    }

    @Transactional
    public void execute(Long roleId, AssignRoleRequest request) {
        Role role = roleRepository.getOrThrow(roleId);
        rolePolicy.checkCanGiveRole(role);
        User user = userRepository.getOrThrow(request.userId());
        user.getRoles().add(role);
        cacheEvictor.evict(user.getUsrId());
    }
}
