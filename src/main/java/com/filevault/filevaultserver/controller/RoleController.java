package com.filevault.filevaultserver.controller;

import com.filevault.filevaultserver.action.role.AssignRoleAction;
import com.filevault.filevaultserver.action.role.CreateRoleAction;
import com.filevault.filevaultserver.action.role.DeleteRoleAction;
import com.filevault.filevaultserver.action.role.GetRoleAction;
import com.filevault.filevaultserver.action.role.ListRolesAction;
import com.filevault.filevaultserver.action.role.UnassignRoleAction;
import com.filevault.filevaultserver.action.role.UpdateRoleAction;
import com.filevault.filevaultserver.request.role.AssignRoleRequest;
import com.filevault.filevaultserver.request.role.CreateRoleRequest;
import com.filevault.filevaultserver.request.role.UpdateRoleRequest;
import com.filevault.filevaultserver.response.role.RoleResponse;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final CreateRoleAction createRoleAction;
    private final ListRolesAction listRolesAction;
    private final GetRoleAction getRoleAction;
    private final UpdateRoleAction updateRoleAction;
    private final DeleteRoleAction deleteRoleAction;
    private final AssignRoleAction assignRoleAction;
    private final UnassignRoleAction unassignRoleAction;

    public RoleController(
            CreateRoleAction createRoleAction,
            ListRolesAction listRolesAction,
            GetRoleAction getRoleAction,
            UpdateRoleAction updateRoleAction,
            DeleteRoleAction deleteRoleAction,
            AssignRoleAction assignRoleAction,
            UnassignRoleAction unassignRoleAction) {
        this.createRoleAction = createRoleAction;
        this.listRolesAction = listRolesAction;
        this.getRoleAction = getRoleAction;
        this.updateRoleAction = updateRoleAction;
        this.deleteRoleAction = deleteRoleAction;
        this.assignRoleAction = assignRoleAction;
        this.unassignRoleAction = unassignRoleAction;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('roles:create')")
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody CreateRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(createRoleAction.execute(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('roles:read')")
    public List<RoleResponse> list() {
        return listRolesAction.execute();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('roles:read')")
    public RoleResponse get(@PathVariable("id") Long id) {
        return getRoleAction.execute(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('roles:update')")
    public RoleResponse update(@PathVariable("id") Long id, @Valid @RequestBody UpdateRoleRequest request) {
        return updateRoleAction.execute(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('roles:delete')")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        deleteRoleAction.execute(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('roles:assign')")
    public ResponseEntity<Void> assign(@PathVariable("id") Long id, @Valid @RequestBody AssignRoleRequest request) {
        assignRoleAction.execute(id, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/assign/{userId}")
    @PreAuthorize("hasAuthority('roles:assign')")
    public ResponseEntity<Void> unassign(@PathVariable("id") Long id, @PathVariable UUID userId) {
        unassignRoleAction.execute(id, userId);
        return ResponseEntity.noContent().build();
    }
}
