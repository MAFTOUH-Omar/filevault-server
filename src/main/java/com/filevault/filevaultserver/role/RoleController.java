package com.filevault.filevaultserver.role;

import com.filevault.filevaultserver.role.dto.AssignRoleRequest;
import com.filevault.filevaultserver.role.dto.RoleCreateRequest;
import com.filevault.filevaultserver.role.dto.RoleResponse;
import com.filevault.filevaultserver.role.dto.RoleUpdateRequest;
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

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('roles:create')")
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody RoleCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roleService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('roles:read')")
    public List<RoleResponse> list() {
        return roleService.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('roles:read')")
    public RoleResponse get(@PathVariable("id") Long id) {
        return roleService.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('roles:update')")
    public RoleResponse update(@PathVariable("id") Long id, @Valid @RequestBody RoleUpdateRequest request) {
        return roleService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('roles:delete')")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        roleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('roles:assign')")
    public ResponseEntity<Void> assign(@PathVariable("id") Long id, @Valid @RequestBody AssignRoleRequest request) {
        roleService.assign(id, request.userId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/assign/{userId}")
    @PreAuthorize("hasAuthority('roles:assign')")
    public ResponseEntity<Void> unassign(@PathVariable("id") Long id, @PathVariable UUID userId) {
        roleService.unassign(id, userId);
        return ResponseEntity.noContent().build();
    }
}
