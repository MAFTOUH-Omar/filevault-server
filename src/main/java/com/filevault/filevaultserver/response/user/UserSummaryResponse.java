package com.filevault.filevaultserver.response.user;

import com.filevault.filevaultserver.models.User;
import com.filevault.filevaultserver.security.AuthorityMapper;
import java.util.List;
import java.util.UUID;

public record UserSummaryResponse(UUID id, String email, String fullName, List<String> roles, List<String> permissions) {

    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(
                user.getUsrId(),
                user.getUsrEmail(),
                user.getUsrFullName(),
                AuthorityMapper.roleNames(user).stream().sorted().toList(),
                AuthorityMapper.permissionNames(user).stream().sorted().toList());
    }
}
