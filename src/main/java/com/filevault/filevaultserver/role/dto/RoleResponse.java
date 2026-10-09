package com.filevault.filevaultserver.role.dto;

import java.util.List;

public record RoleResponse(Long id, String name, Long storageQuotaBytes, List<String> permissions) {
}
