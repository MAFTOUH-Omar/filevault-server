package com.filevault.filevaultserver.role;

public class RoleNotFoundException extends RuntimeException {

    public RoleNotFoundException(Long rolId) {
        super("Role not found: " + rolId);
    }
}
