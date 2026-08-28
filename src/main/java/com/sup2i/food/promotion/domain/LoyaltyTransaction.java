package com.sup2i.food.promotion.domain;

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
@Table(name = "loyalty_transactions")
public class LoyaltyTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private LoyaltyAccount account;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private LoyaltyTransactionType type;

    @Column(name = "points", nullable = false)
    private int points;

    @Column(name = "reference_type", length = 40)
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "reward_id")
    private UUID rewardId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "refund_id")
    private UUID refundId;

    protected LoyaltyTransaction() {
    }

    public LoyaltyTransaction(
        LoyaltyAccount account,
        LoyaltyTransactionType type,
        int points,
        String referenceType,
        UUID referenceId,
        String reason,
        UUID createdBy,
        UUID orderId
    ) {
        this.account = account;
        this.type = type;
        this.points = points;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.reason = reason;
        this.createdBy = createdBy;
        this.orderId = orderId;
    }

    public UUID getId() {
        return id;
    }

    public LoyaltyAccount getAccount() {
        return account;
    }

    public LoyaltyTransactionType getType() {
        return type;
    }

    public int getPoints() {
        return points;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public String getReason() {
        return reason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}