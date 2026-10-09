package com.filevault.filevaultserver.role;

import com.filevault.filevaultserver.permission.Permission;
import com.filevault.filevaultserver.role.dto.RoleCreateRequest;
import com.filevault.filevaultserver.role.dto.RoleResponse;
import com.filevault.filevaultserver.role.dto.RoleUpdateRequest;
import com.filevault.filevaultserver.user.User;
import com.filevault.filevaultserver.user.UserNotFoundException;
import com.filevault.filevaultserver.user.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    public RoleService(RoleRepository roleRepository, UserRepository userRepository) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public RoleResponse create(RoleCreateRequest request) {
        if (roleRepository.existsByRolName(request.name())) {
            throw new RoleAlreadyExistsException(request.name());
        }
        Role role = new Role(request.name(), request.storageQuotaBytes());
        return toResponse(roleRepository.save(role));
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        return roleRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse get(Long rolId) {
        return toResponse(findRoleOrThrow(rolId));
    }

    @Transactional
    public RoleResponse update(Long rolId, RoleUpdateRequest request) {
        Role role = findRoleOrThrow(rolId);
        roleRepository.findByRolName(request.name())
                .filter(existing -> !existing.getRolId().equals(rolId))
                .ifPresent(existing -> {
                    throw new RoleAlreadyExistsException(request.name());
                });
        role.setRolName(request.name());
        role.setRolStorageQuotaBytes(request.storageQuotaBytes());
        return toResponse(role);
    }

    @Transactional
    public void delete(Long rolId) {
        roleRepository.delete(findRoleOrThrow(rolId));
    }

    @Transactional
    public void assign(Long rolId, UUID usrId) {
        Role role = findRoleOrThrow(rolId);
        User user = findUserOrThrow(usrId);
        user.getRoles().add(role);
    }

    @Transactional
    public void unassign(Long rolId, UUID usrId) {
        Role role = findRoleOrThrow(rolId);
        User user = findUserOrThrow(usrId);
        user.getRoles().remove(role);
    }

    private Role findRoleOrThrow(Long rolId) {
        return roleRepository.findById(rolId).orElseThrow(() -> new RoleNotFoundException(rolId));
    }

    private User findUserOrThrow(UUID usrId) {
        return userRepository.findById(usrId).orElseThrow(() -> new UserNotFoundException(usrId));
    }

    private RoleResponse toResponse(Role role) {
        List<String> permissionNames = role.getPermissions().stream()
                .map(Permission::getPrmName)
                .sorted()
                .toList();
        return new RoleResponse(role.getRolId(), role.getRolName(), role.getRolStorageQuotaBytes(), permissionNames);
    }
}
