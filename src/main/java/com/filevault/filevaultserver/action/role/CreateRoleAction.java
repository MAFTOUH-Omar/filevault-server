package com.filevault.filevaultserver.action.role;

import com.filevault.filevaultserver.exception.role.RoleAlreadyExistsException;
import com.filevault.filevaultserver.repository.role.RoleRepository;
import com.filevault.filevaultserver.request.role.CreateRoleRequest;
import com.filevault.filevaultserver.response.role.RoleResponse;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CreateRoleAction {

    private final RoleRepository roleRepository;
    private final RoleProvisioner roleProvisioner;

    public CreateRoleAction(RoleRepository roleRepository, RoleProvisioner roleProvisioner) {
        this.roleRepository = roleRepository;
        this.roleProvisioner = roleProvisioner;
    }

    @Transactional
    public RoleResponse execute(CreateRoleRequest request) {
        if (roleRepository.existsByRolName(request.name())) {
            throw new RoleAlreadyExistsException(request.name());
        }
        return RoleResponse.from(roleProvisioner.createRoleWithGrantPermission(request.name(), request.storageQuotaBytes()));
    }
}
