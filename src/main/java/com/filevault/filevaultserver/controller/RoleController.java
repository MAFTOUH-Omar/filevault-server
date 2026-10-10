package com.filevault.filevaultserver.controller;

import com.filevault.filevaultserver.action.role.AssignRoleAction;
import com.filevault.filevaultserver.action.role.CreateRoleAction;
import com.filevault.filevaultserver.action.role.DeleteRoleAction;
import com.filevault.filevaultserver.action.role.GetRoleAction;
import com.filevault.filevaultserver.action.role.GetRolePermissionsAction;
import com.filevault.filevaultserver.action.role.ListRolesAction;
import com.filevault.filevaultserver.action.role.UnassignRoleAction;
import com.filevault.filevaultserver.action.role.UpdateRoleAction;
import com.filevault.filevaultserver.middleware.ErrorResponse;
import com.filevault.filevaultserver.request.role.AssignRoleRequest;
import com.filevault.filevaultserver.request.role.CreateRoleRequest;
import com.filevault.filevaultserver.request.role.UpdateRoleRequest;
import com.filevault.filevaultserver.response.PageResponse;
import com.filevault.filevaultserver.response.role.PermissionResponse;
import com.filevault.filevaultserver.response.role.RoleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/roles")
@Tag(name = "Roles", description = "Role, permission, and role-assignment management")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(
            responseCode = "401",
            description = "Missing, invalid, or expired access token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
            responseCode = "429",
            description = "Too many requests",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class RoleController {

    private final CreateRoleAction createRoleAction;
    private final ListRolesAction listRolesAction;
    private final GetRoleAction getRoleAction;
    private final GetRolePermissionsAction getRolePermissionsAction;
    private final UpdateRoleAction updateRoleAction;
    private final DeleteRoleAction deleteRoleAction;
    private final AssignRoleAction assignRoleAction;
    private final UnassignRoleAction unassignRoleAction;

    public RoleController(
            CreateRoleAction createRoleAction,
            ListRolesAction listRolesAction,
            GetRoleAction getRoleAction,
            GetRolePermissionsAction getRolePermissionsAction,
            UpdateRoleAction updateRoleAction,
            DeleteRoleAction deleteRoleAction,
            AssignRoleAction assignRoleAction,
            UnassignRoleAction unassignRoleAction) {
        this.createRoleAction = createRoleAction;
        this.listRolesAction = listRolesAction;
        this.getRoleAction = getRoleAction;
        this.getRolePermissionsAction = getRolePermissionsAction;
        this.updateRoleAction = updateRoleAction;
        this.deleteRoleAction = deleteRoleAction;
        this.assignRoleAction = assignRoleAction;
        this.unassignRoleAction = unassignRoleAction;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('roles:create')")
    @Operation(summary = "Create a role")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Role created"),
        @ApiResponse(
                responseCode = "400",
                description = "Validation error",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Caller lacks the 'roles:create' permission",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "A role with this name already exists",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody CreateRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(createRoleAction.execute(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('roles:read')")
    @Operation(
            summary = "List roles, with optional search and pagination",
            description = "'search' matches role names containing the given text (case-insensitive); "
                    + "'%' and '_' in it are treated as literal characters, not SQL wildcards. "
                    + "'size' is capped at 100.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Roles listed"),
        @ApiResponse(
                responseCode = "403",
                description = "Caller lacks the 'roles:read' permission",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public PageResponse<RoleResponse> list(
            @Parameter(description = "Case-insensitive substring match on role name") @RequestParam(required = false)
                    String search,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, capped at 100") @RequestParam(defaultValue = "20") int size) {
        return listRolesAction.execute(search, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('roles:read')")
    @Operation(summary = "Get a role by id")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Role found"),
        @ApiResponse(
                responseCode = "403",
                description = "Caller lacks the 'roles:read' permission",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "No role with this id",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public RoleResponse get(@PathVariable("id") Long id) {
        return getRoleAction.execute(id);
    }

    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('roles:read')")
    @Operation(summary = "List the permissions granted to a role")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Permissions listed"),
        @ApiResponse(
                responseCode = "403",
                description = "Caller lacks the 'roles:read' permission",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "No role with this id",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public List<PermissionResponse> permissions(@PathVariable("id") Long id) {
        return getRolePermissionsAction.execute(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('roles:update')")
    @Operation(summary = "Rename a role or change its storage quota")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Role updated"),
        @ApiResponse(
                responseCode = "400",
                description = "Validation error",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Caller lacks the 'roles:update' permission",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "No role with this id",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Another role already uses this name",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public RoleResponse update(@PathVariable("id") Long id, @Valid @RequestBody UpdateRoleRequest request) {
        return updateRoleAction.execute(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('roles:delete')")
    @Operation(
            summary = "Delete a role",
            description = "Refused if any user still has this role, unless force=true — in which case those "
                    + "users are moved onto an 'archive-<name>' role (created if needed) before deletion, so "
                    + "there's always a record that they used to have it.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Role deleted"),
        @ApiResponse(
                responseCode = "403",
                description = "Caller lacks the 'roles:delete' permission",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "No role with this id",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Role still has users assigned and force was not set",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> delete(
            @PathVariable("id") Long id,
            @Parameter(description = "Archive-and-transfer affected users instead of refusing")
                    @RequestParam(defaultValue = "false")
                    boolean force) {
        deleteRoleAction.execute(id, force);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('roles:assign')")
    @Operation(summary = "Grant a role to a user", description =
            "Requires the generic 'roles:assign' permission AND the role-specific "
                    + "'roles:give:<name>' permission for the role being granted.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Role granted"),
        @ApiResponse(
                responseCode = "400",
                description = "Validation error",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Caller lacks 'roles:assign', or lacks 'roles:give:<name>' for this specific role",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "No role or user with this id",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> assign(@PathVariable("id") Long id, @Valid @RequestBody AssignRoleRequest request) {
        assignRoleAction.execute(id, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/assign/{userId}")
    @PreAuthorize("hasAuthority('roles:assign')")
    @Operation(summary = "Revoke a role from a user", description =
            "Requires the generic 'roles:assign' permission AND the role-specific "
                    + "'roles:give:<name>' permission for the role being revoked.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Role revoked"),
        @ApiResponse(
                responseCode = "403",
                description = "Caller lacks 'roles:assign', or lacks 'roles:give:<name>' for this specific role",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "No role or user with this id",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> unassign(@PathVariable("id") Long id, @PathVariable UUID userId) {
        unassignRoleAction.execute(id, userId);
        return ResponseEntity.noContent().build();
    }
}
