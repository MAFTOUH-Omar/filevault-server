package com.filevault.filevaultserver.action.role;

import com.filevault.filevaultserver.repository.role.RoleRepository;
import com.filevault.filevaultserver.response.role.RoleResponse;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ListRolesAction {

    private final RoleRepository roleRepository;

    public ListRolesAction(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> execute() {
        return roleRepository.findAll().stream().map(RoleResponse::from).toList();
    }
}
