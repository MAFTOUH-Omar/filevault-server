package com.filevault.filevaultserver.action.role;

import com.filevault.filevaultserver.repository.RoleRepository;
import com.filevault.filevaultserver.response.role.RoleResponse;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GetRoleAction {

    private final RoleRepository roleRepository;

    public GetRoleAction(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Transactional(readOnly = true)
    public RoleResponse execute(Long roleId) {
        return RoleResponse.from(roleRepository.getOrThrow(roleId));
    }
}
