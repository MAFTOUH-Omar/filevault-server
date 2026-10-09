package com.filevault.filevaultserver.exception.user;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID usrId) {
        super("User not found: " + usrId);
    }
}
