package com.filevault.filevaultserver.permission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "prm_permissions")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "prm_id")
    private Long prmId;

    @Column(name = "prm_name", nullable = false, unique = true)
    private String prmName;

    @Column(name = "prm_description")
    private String prmDescription;

    protected Permission() {
    }

    public Permission(String prmName, String prmDescription) {
        this.prmName = prmName;
        this.prmDescription = prmDescription;
    }

    public Long getPrmId() {
        return prmId;
    }

    public String getPrmName() {
        return prmName;
    }

    public String getPrmDescription() {
        return prmDescription;
    }

    public void setPrmDescription(String prmDescription) {
        this.prmDescription = prmDescription;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Permission other)) {
            return false;
        }
        return prmName != null && prmName.equals(other.prmName);
    }

    @Override
    public int hashCode() {
        return prmName == null ? 0 : prmName.hashCode();
    }
}
