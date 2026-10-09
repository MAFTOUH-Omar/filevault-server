package com.filevault.filevaultserver.exception.role;

/**
 * Thrown when the caller has the generic {@code roles:assign} authority but lacks the specific
 * {@code roles:give:<name>} permission for the role being granted or revoked — prevents a holder
 * of a broad "can assign roles" permission from handing out a role more privileged than they are
 * themselves authorized to give (e.g. granting 'admin' without 'roles:give:admin').
 */
public class ForbiddenRoleGrantException extends RuntimeException {

    public ForbiddenRoleGrantException(String rolName) {
        super("Not authorized to give role: " + rolName);
    }
}
