package com.filevault.filevaultserver.action.role;

import com.filevault.filevaultserver.repository.role.RoleRepository;
import com.filevault.filevaultserver.response.role.PermissionResponse;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GetRolePermissionsAction {

    private final RoleRepository roleRepository;

    public GetRolePermissionsAction(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> execute(Long roleId) {
        return roleRepository.getOrThrow(roleId).getPermissions().stream()
                .map(PermissionResponse::from)
                .sorted(Comparator.comparing(PermissionResponse::name))
                .toList();
    }
}
