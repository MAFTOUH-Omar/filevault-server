package com.filevault.filevaultserver.response.user;

import com.filevault.filevaultserver.models.User;
import com.filevault.filevaultserver.security.AuthorityMapper;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/** Serializable so Spring's RedisCacheManager can store it (see MeAction / UserSummaryCacheEvictor). */
public record UserSummaryResponse(UUID id, String email, String fullName, List<String> roles, List<String> permissions)
        implements Serializable {

    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(
                user.getUsrId(),
                user.getUsrEmail(),
                user.getUsrFullName(),
                AuthorityMapper.roleNames(user).stream().sorted().toList(),
                AuthorityMapper.permissionNames(user).stream().sorted().toList());
    }
}
