package com.filevault.filevaultserver.request.role;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignRoleRequest(@NotNull UUID userId) {
}
