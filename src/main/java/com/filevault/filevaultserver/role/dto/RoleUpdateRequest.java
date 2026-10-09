package com.filevault.filevaultserver.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RoleUpdateRequest(
        @NotBlank @Size(max = 50) String name, @Positive Long storageQuotaBytes) {
}
