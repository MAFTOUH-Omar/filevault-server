package com.filevault.filevaultserver.response.user;

import com.filevault.filevaultserver.models.Role;
import com.filevault.filevaultserver.models.User;
import com.filevault.filevaultserver.security.AuthorityMapper;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * Serializable so Spring's RedisCacheManager can store it (see MeAction / UserSummaryCacheEvictor).
 *
 * <p>{@code storageQuotaBytes} is the quota the user's roles grant them (the largest one, same rule as
 * {@code UserRepository.reserveStorage}) and {@code storageRemainingBytes} what is left of it; both are
 * {@code null} when the quota is unlimited.
 */
public record UserSummaryResponse(
        UUID id,
        String email,
        String fullName,
        long storageUsedBytes,
        Long storageQuotaBytes,
        Long storageRemainingBytes,
        List<String> roles,
        List<String> permissions)
        implements Serializable {

    public static UserSummaryResponse from(User user) {
        long usedBytes = user.getUsrStorageUsedBytes();
        Long quotaBytes = effectiveQuotaBytes(user);
        Long remainingBytes = quotaBytes == null ? null : Math.max(quotaBytes - usedBytes, 0L);
        return new UserSummaryResponse(
                user.getUsrId(),
                user.getUsrEmail(),
                user.getUsrFullName(),
                usedBytes,
                quotaBytes,
                remainingBytes,
                AuthorityMapper.roleNames(user).stream().sorted().toList(),
                AuthorityMapper.permissionNames(user).stream().sorted().toList());
    }

    /** Largest role quota; {@code null} (unlimited) if any role is unlimited; 0 for a user with no role. */
    private static Long effectiveQuotaBytes(User user) {
        long maxBytes = 0L;
        for (Role role : user.getRoles()) {
            Long roleQuotaBytes = role.getRolStorageQuotaBytes();
            if (roleQuotaBytes == null) {
                return null;
            }
            maxBytes = Math.max(maxBytes, roleQuotaBytes);
        }
        return maxBytes;
    }
}
