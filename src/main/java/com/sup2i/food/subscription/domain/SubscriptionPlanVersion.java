package com.sup2i.food.subscription.domain;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "subscription_plan_versions")
public class SubscriptionPlanVersion {

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
        name = "plan_id",
        nullable = false
    )
    private SubscriptionPlan plan;

    @Column(
        name = "version_number",
        nullable = false
    )
    private int versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "audience_type",
        nullable = false,
        length = 20
    )
    private AudienceType audienceType;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "billing_period",
        nullable = false,
        length = 30
    )
    private BillingPeriod billingPeriod;

    @Column(
        name = "price",
        nullable = false,
        precision = 12,
        scale = 2
    )
    private BigDecimal price;

    @Column(name = "included_meals")
    private Integer includedMeals;

    @Column(name = "validity_days")
    private Integer validityDays;

    @Column(
        name = "quota_type",
        length = 30
    )
    private String quotaType;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "quota_period_type",
        nullable = false,
        length = 30
    )
    private QuotaPeriodType quotaPeriodType;

    @Column(name = "quota_value")
    private Integer quotaValue;

    @Column(name = "max_per_day")
    private Integer maxPerDay;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(
        name = "allowed_days",
        columnDefinition = "smallint[]"
    )
    private Short[] allowedDays;

    @Column(
        name = "reservation_required",
        nullable = false
    )
    private boolean reservationRequired;

    @Column(name = "reservation_deadline")
    private LocalTime reservationDeadline;

    @Column(
        name = "reservation_cancellation_deadline"
    )
    private LocalTime reservationCancellationDeadline;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "renewal_policy",
        nullable = false,
        length = 30
    )
    private RenewalPolicy renewalPolicy;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "suspension_policy",
        nullable = false,
        length = 30
    )
    private SuspensionPolicy suspensionPolicy;

    @Column(name = "academic_calendar_id")
    private UUID academicCalendarId;

    @Column(name = "sale_starts_at")
    private OffsetDateTime saleStartsAt;

    @Column(name = "sale_ends_at")
    private OffsetDateTime saleEndsAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
        name = "rules",
        nullable = false,
        columnDefinition = "jsonb"
    )
    private String rules = "{}";

    @Column(
        name = "effective_from",
        nullable = false
    )
    private OffsetDateTime effectiveFrom;

    @Column(name = "effective_to")
    private OffsetDateTime effectiveTo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @CreationTimestamp
    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private OffsetDateTime createdAt;

    protected SubscriptionPlanVersion() {
    }

    public SubscriptionPlanVersion(
        SubscriptionPlan plan,
        int versionNumber,
        AudienceType audienceType,
        BillingPeriod billingPeriod,
        BigDecimal price,
        QuotaPeriodType quotaPeriodType,
        RenewalPolicy renewalPolicy,
        SuspensionPolicy suspensionPolicy,
        OffsetDateTime effectiveFrom
    ) {
        this.plan =
            plan;

        this.versionNumber =
            versionNumber;

        this.audienceType =
            audienceType;

        this.billingPeriod =
            billingPeriod;

        this.price =
            price;

        this.quotaPeriodType =
            quotaPeriodType;

        this.renewalPolicy =
            renewalPolicy;

        this.suspensionPolicy =
            suspensionPolicy;

        this.effectiveFrom =
            effectiveFrom;
    }

    public UUID getId() {
        return id;
    }

    public SubscriptionPlan getPlan() {
        return plan;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public AudienceType getAudienceType() {
        return audienceType;
    }

    public BillingPeriod getBillingPeriod() {
        return billingPeriod;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Integer getIncludedMeals() {
        return includedMeals;
    }

    public void setIncludedMeals(
        Integer includedMeals
    ) {
        this.includedMeals =
            includedMeals;
    }

    public Integer getValidityDays() {
        return validityDays;
    }

    public void setValidityDays(
        Integer validityDays
    ) {
        this.validityDays =
            validityDays;
    }

    public String getQuotaType() {
        return quotaType;
    }

    public void setQuotaType(
        String quotaType
    ) {
        this.quotaType =
            quotaType;
    }

    public QuotaPeriodType getQuotaPeriodType() {
        return quotaPeriodType;
    }

    public Integer getQuotaValue() {
        return quotaValue;
    }

    public void setQuotaValue(
        Integer quotaValue
    ) {
        this.quotaValue =
            quotaValue;
    }

    public Integer getMaxPerDay() {
        return maxPerDay;
    }

    public void setMaxPerDay(
        Integer maxPerDay
    ) {
        this.maxPerDay =
            maxPerDay;
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

    public boolean isReservationRequired() {
        return reservationRequired;
    }

    public void setReservationRequired(
        boolean reservationRequired
    ) {
        this.reservationRequired =
            reservationRequired;
    }

    public LocalTime getReservationDeadline() {
        return reservationDeadline;
    }

    public void setReservationDeadline(
        LocalTime reservationDeadline
    ) {
        this.reservationDeadline =
            reservationDeadline;
    }

    public LocalTime
        getReservationCancellationDeadline() {
        return reservationCancellationDeadline;
    }

    public void
        setReservationCancellationDeadline(
            LocalTime value
        ) {
        this.reservationCancellationDeadline =
            value;
    }

    public RenewalPolicy getRenewalPolicy() {
        return renewalPolicy;
    }

    public SuspensionPolicy getSuspensionPolicy() {
        return suspensionPolicy;
    }

    public UUID getAcademicCalendarId() {
        return academicCalendarId;
    }

    public void setAcademicCalendarId(
        UUID academicCalendarId
    ) {
        this.academicCalendarId =
            academicCalendarId;
    }

    public OffsetDateTime getSaleStartsAt() {
        return saleStartsAt;
    }

    public void setSaleStartsAt(
        OffsetDateTime saleStartsAt
    ) {
        this.saleStartsAt =
            saleStartsAt;
    }

    public OffsetDateTime getSaleEndsAt() {
        return saleEndsAt;
    }

    public void setSaleEndsAt(
        OffsetDateTime saleEndsAt
    ) {
        this.saleEndsAt =
            saleEndsAt;
    }

    public String getRules() {
        return rules;
    }

    public OffsetDateTime getEffectiveFrom() {
        return effectiveFrom;
    }

    public OffsetDateTime getEffectiveTo() {
        return effectiveTo;
    }

    public void close(
        OffsetDateTime effectiveTo
    ) {
        this.effectiveTo =
            effectiveTo;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(
        User createdBy
    ) {
        this.createdBy =
            createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
