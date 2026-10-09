package com.filevault.filevaultserver.request.role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateRoleRequest(@NotBlank @Size(max = 50) String name, @Positive Long storageQuotaBytes) {
}
