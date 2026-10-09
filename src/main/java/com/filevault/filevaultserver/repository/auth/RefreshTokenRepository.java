package com.filevault.filevaultserver.repository.auth;

import com.filevault.filevaultserver.models.RefreshToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokTokenHash(String tokTokenHash);

    List<RefreshToken> findByUser_UsrIdAndTokRevokedAtIsNull(UUID usrId);
}
