package com.filevault.filevaultserver.role;

public class RoleAlreadyExistsException extends RuntimeException {

    public RoleAlreadyExistsException(String rolName) {
        super("Role already exists: " + rolName);
    }
}
