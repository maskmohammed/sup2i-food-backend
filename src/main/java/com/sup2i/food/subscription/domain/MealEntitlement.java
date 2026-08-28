package com.sup2i.food.subscription.domain;

import com.sup2i.food.common.domain.MealType;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "meal_entitlements")
public class MealEntitlement {

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
        name = "subscription_id",
        nullable = false
    )
    private Subscription subscription;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "meal_type",
        nullable = false,
        length = 30
    )
    private MealType mealType;

    @Column(
        name = "valid_from",
        nullable = false
    )
    private LocalDate validFrom;

    @Column(
        name = "valid_to",
        nullable = false
    )
    private LocalDate validTo;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(
        name = "allowed_days",
        columnDefinition = "smallint[]"
    )
    private Short[] allowedDays;

    @Column(name = "total_quota")
    private Integer totalQuota;

    @Column(
        name = "daily_limit",
        nullable = false
    )
    private int dailyLimit = 1;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "quota_period_type",
        nullable = false,
        length = 30
    )
    private QuotaPeriodType quotaPeriodType =
        QuotaPeriodType.SUBSCRIPTION;

    @Column(
        name = "reservation_required",
        nullable = false
    )
    private boolean reservationRequired;

    @CreationTimestamp
    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private OffsetDateTime createdAt;

    protected MealEntitlement() {
    }

    public MealEntitlement(
        Subscription subscription,
        MealType mealType,
        LocalDate validFrom,
        LocalDate validTo,
        Integer totalQuota,
        int dailyLimit,
        QuotaPeriodType quotaPeriodType,
        boolean reservationRequired
    ) {
        this.subscription =
            subscription;

        this.mealType =
            mealType;

        this.validFrom =
            validFrom;

        this.validTo =
            validTo;

        this.totalQuota =
            totalQuota;

        this.dailyLimit =
            dailyLimit;

        this.quotaPeriodType =
            quotaPeriodType;

        this.reservationRequired =
            reservationRequired;
    }

    public UUID getId() {
        return id;
    }

    public Subscription getSubscription() {
        return subscription;
    }

    public MealType getMealType() {
        return mealType;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public Short[] getAllowedDays() {
        return allowedDays;
    }

    public void setAllowedDays(
        Short[] allowedDays
    ) {
        this.allowedDays =
            allowedDays;
    }

    public Integer getTotalQuota() {
        return totalQuota;
    }

    public int getDailyLimit() {
        return dailyLimit;
    }

    public QuotaPeriodType getQuotaPeriodType() {
        return quotaPeriodType;
    }

    public boolean isReservationRequired() {
        return reservationRequired;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
