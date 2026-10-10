package com.filevault.filevaultserver.exception.role;

public class RoleHasUsersException extends RuntimeException {

    public RoleHasUsersException(String rolName, int userCount) {
        super("Role '" + rolName + "' still has " + userCount
                + " user(s) assigned; pass force=true to archive them and delete the role");
    }
}
