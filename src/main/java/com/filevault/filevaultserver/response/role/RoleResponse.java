package com.filevault.filevaultserver.response.role;

import com.filevault.filevaultserver.models.Permission;
import com.filevault.filevaultserver.models.Role;
import java.util.List;

public record RoleResponse(Long id, String name, Long storageQuotaBytes, List<String> permissions) {

    public static RoleResponse from(Role role) {
        List<String> permissionNames = role.getPermissions().stream()
                .map(Permission::getPrmName)
                .sorted()
                .toList();
        return new RoleResponse(role.getRolId(), role.getRolName(), role.getRolStorageQuotaBytes(), permissionNames);
    }
}
