package com.filevault.filevaultserver.auth;

import com.filevault.filevaultserver.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tok_refresh_tokens")
public class RefreshToken {

    @Id
    @Column(name = "tok_id")
    private UUID tokId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usr_id", nullable = false)
    private User user;

    @Column(name = "tok_token_hash", nullable = false, unique = true)
    private String tokTokenHash;

    @Column(name = "tok_expires_at", nullable = false)
    private Instant tokExpiresAt;

    @Column(name = "tok_revoked_at")
    private Instant tokRevokedAt;

    @Column(name = "tok_created_at", nullable = false)
    private Instant tokCreatedAt;

    protected RefreshToken() {
    }

    public RefreshToken(User user, String tokTokenHash, Instant tokExpiresAt) {
        this.tokId = UUID.randomUUID();
        this.user = user;
        this.tokTokenHash = tokTokenHash;
        this.tokExpiresAt = tokExpiresAt;
        this.tokCreatedAt = Instant.now();
    }

    public boolean isActive() {
        return tokRevokedAt == null && tokExpiresAt.isAfter(Instant.now());
    }

    public void revoke() {
        this.tokRevokedAt = Instant.now();
    }

    public UUID getTokId() {
        return tokId;
    }

    public User getUser() {
        return user;
    }

    public String getTokTokenHash() {
        return tokTokenHash;
    }

    public Instant getTokExpiresAt() {
        return tokExpiresAt;
    }

    public Instant getTokRevokedAt() {
        return tokRevokedAt;
    }

    public Instant getTokCreatedAt() {
        return tokCreatedAt;
    }
}
