package com.filevault.filevaultserver.repository.security;

import com.filevault.filevaultserver.models.BlacklistedIp;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlacklistedIpRepository extends JpaRepository<BlacklistedIp, Long> {

    boolean existsBySecIpAddress(String secIpAddress);
}
