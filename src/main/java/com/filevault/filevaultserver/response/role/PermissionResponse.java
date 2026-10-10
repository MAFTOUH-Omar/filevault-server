package com.filevault.filevaultserver.response.role;

import com.filevault.filevaultserver.models.Permission;

public record PermissionResponse(String name, String description) {

    public static PermissionResponse from(Permission permission) {
        return new PermissionResponse(permission.getPrmName(), permission.getPrmDescription());
    }
}
