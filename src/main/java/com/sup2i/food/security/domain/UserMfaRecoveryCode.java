package com.sup2i.food.security.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_mfa_recovery_codes")
public class UserMfaRecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mfa_method_id", nullable = false)
    private UserMfaMethod mfaMethod;

    @Column(name = "code_hash", nullable = false, unique = true, length = 255)
    private String codeHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    protected UserMfaRecoveryCode() {
    }

    public UserMfaRecoveryCode(
        UserMfaMethod mfaMethod,
        String codeHash
    ) {
        this.mfaMethod = mfaMethod;
        this.codeHash = codeHash;
    }

    public UUID getId() {
        return id;
    }

    public UserMfaMethod getMfaMethod() {
        return mfaMethod;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUsedAt() {
        return usedAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isAvailable() {
        return usedAt == null && revokedAt == null;
    }

    public void markUsed(OffsetDateTime usedAt) {
        this.usedAt = usedAt;
    }

    public void revoke(OffsetDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }
}