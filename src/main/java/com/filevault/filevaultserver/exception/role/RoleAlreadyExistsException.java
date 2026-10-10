package com.filevault.filevaultserver.exception.role;

public class RoleAlreadyExistsException extends RuntimeException {

    public RoleAlreadyExistsException(String rolName) {
        super("Role already exists: " + rolName);
    }
}
