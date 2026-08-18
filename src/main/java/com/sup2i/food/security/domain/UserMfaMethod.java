package com.sup2i.food.security.domain;

import com.sup2i.food.identity.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "user_mfa_methods")
public class UserMfaMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "method_type", nullable = false, length = 30)
    private MfaMethodType methodType;

    @Column(name = "label", length = 120)
    private String label;

    @Column(name = "secret_ciphertext")
    private byte[] secretCiphertext;

    @Column(name = "external_credential_id", length = 500)
    private String externalCredentialId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private MfaMethodStatus status = MfaMethodStatus.PENDING;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "last_used_at")
    private OffsetDateTime lastUsedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "disabled_at")
    private OffsetDateTime disabledAt;

    protected UserMfaMethod() {
    }

    public UserMfaMethod(
        User user,
        MfaMethodType methodType
    ) {
        this.user = user;
        this.methodType = methodType;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public MfaMethodType getMethodType() {
        return methodType;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public byte[] getSecretCiphertext() {
        return secretCiphertext;
    }

    public void setSecretCiphertext(byte[] secretCiphertext) {
        this.secretCiphertext = secretCiphertext;
    }

    public String getExternalCredentialId() {
        return externalCredentialId;
    }

    public void setExternalCredentialId(String externalCredentialId) {
        this.externalCredentialId = externalCredentialId;
    }

    public MfaMethodStatus getStatus() {
        return status;
    }

    public boolean isPrimary() {
        return primary;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    public OffsetDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public OffsetDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getDisabledAt() {
        return disabledAt;
    }

    public void activate(OffsetDateTime verifiedAt) {
        this.status = MfaMethodStatus.ACTIVE;
        this.verifiedAt = verifiedAt;
        this.disabledAt = null;
    }

    public void markUsed(OffsetDateTime lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public void disable(OffsetDateTime disabledAt) {
        this.status = MfaMethodStatus.DISABLED;
        this.disabledAt = disabledAt;
        this.primary = false;
    }

    public void revoke(OffsetDateTime disabledAt) {
        this.status = MfaMethodStatus.REVOKED;
        this.disabledAt = disabledAt;
        this.primary = false;
    }
}