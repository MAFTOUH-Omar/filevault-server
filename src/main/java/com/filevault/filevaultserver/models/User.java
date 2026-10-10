package com.filevault.filevaultserver.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "usr_users")
public class User {

    @Id
    @Column(name = "usr_id")
    private UUID usrId;

    @Column(name = "usr_email", nullable = false, unique = true)
    private String usrEmail;

    @Column(name = "usr_password_hash", nullable = false)
    private String usrPasswordHash;

    @Column(name = "usr_full_name", nullable = false)
    private String usrFullName;

    @Column(name = "usr_enabled", nullable = false)
    private boolean usrEnabled;

    @Column(name = "usr_storage_used_bytes", nullable = false)
    private long usrStorageUsedBytes;

    @Column(name = "usr_created_at", nullable = false)
    private Instant usrCreatedAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "usr_rol",
            joinColumns = @JoinColumn(name = "usr_id"),
            inverseJoinColumns = @JoinColumn(name = "rol_id"))
    private Set<Role> roles = new HashSet<>();

    protected User() {
    }

    public User(String usrEmail, String usrPasswordHash, String usrFullName) {
        this.usrId = UUID.randomUUID();
        this.usrEmail = usrEmail;
        this.usrPasswordHash = usrPasswordHash;
        this.usrFullName = usrFullName;
        this.usrEnabled = true;
        this.usrStorageUsedBytes = 0L;
        this.usrCreatedAt = Instant.now();
    }

    public UUID getUsrId() {
        return usrId;
    }

    public String getUsrEmail() {
        return usrEmail;
    }

    public void setUsrEmail(String usrEmail) {
        this.usrEmail = usrEmail;
    }

    public String getUsrPasswordHash() {
        return usrPasswordHash;
    }

    public void setUsrPasswordHash(String usrPasswordHash) {
        this.usrPasswordHash = usrPasswordHash;
    }

    public String getUsrFullName() {
        return usrFullName;
    }

    public boolean isUsrEnabled() {
        return usrEnabled;
    }

    public long getUsrStorageUsedBytes() {
        return usrStorageUsedBytes;
    }

    public Instant getUsrCreatedAt() {
        return usrCreatedAt;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User other)) {
            return false;
        }
        return usrId != null && usrId.equals(other.usrId);
    }

    @Override
    public int hashCode() {
        return usrId == null ? 0 : usrId.hashCode();
    }
}
