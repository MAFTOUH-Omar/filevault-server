package com.filevault.filevaultserver.security;

import com.filevault.filevaultserver.models.User;
import java.util.LinkedHashSet;
import java.util.Set;

public final class AuthorityMapper {

    private AuthorityMapper() {
    }

    /**
     * Every assigned role becomes a {@code ROLE_<NAME>} authority, and every permission attached
     * to those roles becomes its own authority (e.g. {@code roles:create}), so {@code hasRole(...)}
     * and {@code hasAuthority(...)} checks both work against the same JWT claim.
     */
    public static Set<String> toAuthorityStrings(User user) {
        Set<String> authorities = new LinkedHashSet<>();
        user.getRoles().forEach(role -> {
            authorities.add("ROLE_" + role.getRolName().toUpperCase());
            role.getPermissions().forEach(permission -> authorities.add(permission.getPrmName()));
        });
        return authorities;
    }
}
