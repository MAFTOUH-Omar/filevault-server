package com.filevault.filevaultserver.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "sec_blacklisted_ips")
public class BlacklistedIp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sec_id")
    private Long secId;

    @Column(name = "sec_ip_address", nullable = false, unique = true)
    private String secIpAddress;

    @Column(name = "sec_reason")
    private String secReason;

    @Column(name = "sec_created_at", nullable = false)
    private Instant secCreatedAt;

    protected BlacklistedIp() {
    }

    public BlacklistedIp(String secIpAddress, String secReason) {
        this.secIpAddress = secIpAddress;
        this.secReason = secReason;
        this.secCreatedAt = Instant.now();
    }

    public Long getSecId() {
        return secId;
    }

    public String getSecIpAddress() {
        return secIpAddress;
    }

    public String getSecReason() {
        return secReason;
    }

    public Instant getSecCreatedAt() {
        return secCreatedAt;
    }
}
