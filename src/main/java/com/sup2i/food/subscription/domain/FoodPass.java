package com.sup2i.food.subscription.domain;

import com.sup2i.food.identity.domain.Student;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.qr.domain.QrCredential;
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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "food_passes")
public class FoodPass {

    @Id
    @GeneratedValue(
        strategy = GenerationType.UUID
    )
    private UUID id;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "student_id",
        nullable = false
    )
    private Student student;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "credential_id",
        nullable = false,
        unique = true
    )
    private QrCredential credential;

    @Column(
        name = "card_number",
        nullable = false,
        unique = true,
        length = 100
    )
    private String cardNumber;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 30
    )
    private FoodPassStatus status =
        FoodPassStatus.PENDING_ISSUE;

    @CreationTimestamp
    @Column(
        name = "issued_at",
        nullable = false,
        updatable = false
    )
    private OffsetDateTime issuedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_by_id")
    private FoodPass replacedFrom;

    @Column(name = "blocked_at")
    private OffsetDateTime blockedAt;

    @Column(
        name = "block_reason",
        columnDefinition = "text"
    )
    private String blockReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issued_by")
    private User issuedBy;

    @CreationTimestamp
    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(
        name = "updated_at",
        nullable = false
    )
    private OffsetDateTime updatedAt;

    protected FoodPass() {
    }

    public FoodPass(
        Student student,
        QrCredential credential,
        String cardNumber,
        User issuedBy,
        OffsetDateTime expiresAt
    ) {
        this.student =
            student;

        this.credential =
            credential;

        this.cardNumber =
            cardNumber;

        this.issuedBy =
            issuedBy;

        this.expiresAt =
            expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public Student getStudent() {
        return student;
    }

    public QrCredential getCredential() {
        return credential;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public FoodPassStatus getStatus() {
        return status;
    }

    public OffsetDateTime getIssuedAt() {
        return issuedAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public FoodPass getReplacedFrom() {
        return replacedFrom;
    }

    public OffsetDateTime getBlockedAt() {
        return blockedAt;
    }

    public String getBlockReason() {
        return blockReason;
    }

    public User getIssuedBy() {
        return issuedBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void activate() {
        this.status =
            FoodPassStatus.ACTIVE;
    }

    public void markReplaced() {
        this.status =
            FoodPassStatus.REPLACED;
    }

    public void setReplacedFrom(
        FoodPass replacedFrom
    ) {
        this.replacedFrom =
            replacedFrom;
    }

    public void block(
        OffsetDateTime at,
        String reason
    ) {
        this.status =
            FoodPassStatus.BLOCKED;

        this.blockedAt =
            at;

        this.blockReason =
            reason;
    }

    public void reportLost(
        OffsetDateTime at,
        String reason
    ) {
        this.status =
            FoodPassStatus.LOST;

        this.blockedAt =
            at;

        this.blockReason =
            reason;
    }

    public void reactivate(
        QrCredential newCredential
    ) {
        this.status =
            FoodPassStatus.ACTIVE;

        this.credential =
            newCredential;

        this.blockedAt =
            null;

        this.blockReason =
            null;
    }

    public void revoke() {
        this.status =
            FoodPassStatus.REVOKED;
    }

    public void markExpired() {
        this.status =
            FoodPassStatus.EXPIRED;
    }
}