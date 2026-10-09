package com.filevault.filevaultserver.exception;

public class RoleNotFoundException extends RuntimeException {

    public RoleNotFoundException(Long rolId) {
        super("Role not found: " + rolId);
    }
}
