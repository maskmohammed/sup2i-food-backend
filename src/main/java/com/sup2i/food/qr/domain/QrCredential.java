package com.sup2i.food.qr.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "qr_credentials")
public class QrCredential {

    @Id
    @GeneratedValue(
        strategy = GenerationType.UUID
    )
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "credential_type",
        nullable = false,
        length = 30
    )
    private QrCredentialType credentialType;

    @Column(
        name = "subject_id",
        nullable = false
    )
    private UUID subjectId;

    @Column(
        name = "token_hash",
        nullable = false,
        unique = true,
        length = 255
    )
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 30
    )
    private QrCredentialStatus status;

    @CreationTimestamp
    @Column(
        name = "issued_at",
        nullable = false,
        updatable = false
    )
    private OffsetDateTime issuedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "medium",
        nullable = false,
        length = 20
    )
    private QrCredentialMedium medium;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    protected QrCredential() {
    }

    public QrCredential(
        QrCredentialType credentialType,
        UUID subjectId,
        String tokenHash,
        OffsetDateTime expiresAt
    ) {
        this.credentialType =
            credentialType;

        this.subjectId =
            subjectId;

        this.tokenHash =
            tokenHash;

        this.status =
            QrCredentialStatus.ACTIVE;

        this.expiresAt =
            expiresAt;

        this.medium =
            QrCredentialMedium.QR;
    }

    public UUID getId() {
        return id;
    }

    public QrCredentialType getCredentialType() {
        return credentialType;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public QrCredentialStatus getStatus() {
        return status;
    }

    public OffsetDateTime getIssuedAt() {
        return issuedAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public QrCredentialMedium getMedium() {
        return medium;
    }

    public OffsetDateTime getUsedAt() {
        return usedAt;
    }

    public boolean isExpired(
        OffsetDateTime now
    ) {
        return expiresAt != null
            && now.isAfter(expiresAt);
    }

    public void markUsed(
        OffsetDateTime at
    ) {
        status =
            QrCredentialStatus.USED;

        usedAt =
            at;
    }

    public void revoke(
        OffsetDateTime at
    ) {
        status =
            QrCredentialStatus.REVOKED;

        revokedAt =
            at;
    }

    public void expire() {
        status =
            QrCredentialStatus.EXPIRED;
    }
}
