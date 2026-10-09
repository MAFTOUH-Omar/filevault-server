package com.filevault.filevaultserver.role;

import com.filevault.filevaultserver.permission.Permission;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "rol_roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rol_id")
    private Long rolId;

    @Column(name = "rol_name", nullable = false, unique = true)
    private String rolName;

    @Column(name = "rol_storage_quota_bytes")
    private Long rolStorageQuotaBytes;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "rol_prm",
            joinColumns = @JoinColumn(name = "rol_id"),
            inverseJoinColumns = @JoinColumn(name = "prm_id"))
    private Set<Permission> permissions = new HashSet<>();

    protected Role() {
    }

    public Role(String rolName, Long rolStorageQuotaBytes) {
        this.rolName = rolName;
        this.rolStorageQuotaBytes = rolStorageQuotaBytes;
    }

    public Long getRolId() {
        return rolId;
    }

    public String getRolName() {
        return rolName;
    }

    public void setRolName(String rolName) {
        this.rolName = rolName;
    }

    public Long getRolStorageQuotaBytes() {
        return rolStorageQuotaBytes;
    }

    public void setRolStorageQuotaBytes(Long rolStorageQuotaBytes) {
        this.rolStorageQuotaBytes = rolStorageQuotaBytes;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Role other)) {
            return false;
        }
        return rolName != null && rolName.equals(other.rolName);
    }

    @Override
    public int hashCode() {
        return rolName == null ? 0 : rolName.hashCode();
    }
}
