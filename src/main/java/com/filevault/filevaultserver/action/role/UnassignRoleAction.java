package com.filevault.filevaultserver.action.role;

import com.filevault.filevaultserver.models.Role;
import com.filevault.filevaultserver.models.User;
import com.filevault.filevaultserver.policy.RolePolicy;
import com.filevault.filevaultserver.repository.RoleRepository;
import com.filevault.filevaultserver.repository.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UnassignRoleAction {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final RolePolicy rolePolicy;

    public UnassignRoleAction(RoleRepository roleRepository, UserRepository userRepository, RolePolicy rolePolicy) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.rolePolicy = rolePolicy;
    }

    @Transactional
    public void execute(Long roleId, UUID userId) {
        Role role = roleRepository.getOrThrow(roleId);
        rolePolicy.checkCanGiveRole(role);
        User user = userRepository.getOrThrow(userId);
        user.getRoles().remove(role);
    }
}
