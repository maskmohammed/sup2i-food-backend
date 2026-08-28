package com.sup2i.food.subscription.domain;

import com.sup2i.food.identity.domain.User;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "meal_entitlement_adjustments")
public class MealEntitlementAdjustment {

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
        name = "entitlement_id",
        nullable = false
    )
    private MealEntitlement entitlement;

    @Column(
        name = "quota_delta",
        nullable = false
    )
    private int quotaDelta;

    @Column(
        name = "effective_date",
        nullable = false
    )
    private LocalDate effectiveDate;

    @Column(
        name = "reason",
        nullable = false,
        columnDefinition = "text"
    )
    private String reason;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "adjusted_by",
        nullable = false
    )
    private User adjustedBy;

    @CreationTimestamp
    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private OffsetDateTime createdAt;

    protected MealEntitlementAdjustment() {
    }

    public MealEntitlementAdjustment(
        MealEntitlement entitlement,
        int quotaDelta,
        LocalDate effectiveDate,
        String reason,
        User adjustedBy
    ) {
        this.entitlement =
            entitlement;

        this.quotaDelta =
            quotaDelta;

        this.effectiveDate =
            effectiveDate;

        this.reason =
            reason;

        this.adjustedBy =
            adjustedBy;
    }

    public UUID getId() {
        return id;
    }

    public MealEntitlement getEntitlement() {
        return entitlement;
    }

    public int getQuotaDelta() {
        return quotaDelta;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public String getReason() {
        return reason;
    }

    public User getAdjustedBy() {
        return adjustedBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
