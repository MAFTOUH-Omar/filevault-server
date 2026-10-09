package com.filevault.filevaultserver.exception;

public class RoleAlreadyExistsException extends RuntimeException {

    public RoleAlreadyExistsException(String rolName) {
        super("Role already exists: " + rolName);
    }
}
