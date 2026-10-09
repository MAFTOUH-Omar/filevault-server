package com.filevault.filevaultserver.exception.role;

public class RoleNotFoundException extends RuntimeException {

    public RoleNotFoundException(Long rolId) {
        super("Role not found: " + rolId);
    }
}
