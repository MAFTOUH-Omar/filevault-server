package com.filevault.filevaultserver.policy;

import com.filevault.filevaultserver.exception.role.ForbiddenRoleGrantException;
import com.filevault.filevaultserver.models.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Decides who may hand out a given role. Holding the generic {@code roles:assign} authority only
 * lets a caller reach the assign/unassign endpoints; actually granting or revoking role X also
 * requires the caller's token to carry the specific {@code roles:give:X} permission created for
 * that role when it was defined (see CreateRoleAction). Without this, anyone who can assign roles
 * at all could hand out 'admin', which defeats the point of having granular permissions.
 */
@Component
public class RolePolicy {

    public void checkCanGiveRole(Role role) {
        String requiredAuthority = "roles:give:" + role.getRolName();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean allowed = authentication != null
                && authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(requiredAuthority::equals);
        if (!allowed) {
            throw new ForbiddenRoleGrantException(role.getRolName());
        }
    }
}
