package com.sup2i.food.subscription.domain;

import com.sup2i.food.organization.domain.Organization;
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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "subscription_plans")
public class SubscriptionPlan {

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
        name = "organization_id",
        nullable = false
    )
    private Organization organization;

    @Column(
        name = "name",
        nullable = false,
        length = 150
    )
    private String name;

    @Column(
        name = "code",
        nullable = false,
        length = 80
    )
    private String code;

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

    @Column(
        name = "is_active",
        nullable = false
    )
    private boolean active = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
        name = "rules",
        nullable = false,
        columnDefinition = "jsonb"
    )
    private String rules = "{}";

    @Column(name = "academic_calendar_id")
    private UUID academicCalendarId;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "quota_period_type",
        nullable = false,
        length = 30
    )
    private QuotaPeriodType quotaPeriodType =
        QuotaPeriodType.SUBSCRIPTION;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "renewal_policy",
        nullable = false,
        length = 30
    )
    private RenewalPolicy renewalPolicy =
        RenewalPolicy.MANUAL;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "suspension_policy",
        nullable = false,
        length = 30
    )
    private SuspensionPolicy suspensionPolicy =
        SuspensionPolicy.BLOCK_USAGE;

    @Column(name = "reservation_deadline")
    private LocalTime reservationDeadline;

    @Column(
        name = "reservation_cancellation_deadline"
    )
    private LocalTime reservationCancellationDeadline;

    @Column(name = "sale_starts_at")
    private OffsetDateTime saleStartsAt;

    @Column(name = "sale_ends_at")
    private OffsetDateTime saleEndsAt;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "audience_type",
        nullable = false,
        length = 20
    )
    private AudienceType audienceType =
        AudienceType.STUDENT;

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

    protected SubscriptionPlan() {
    }

    public SubscriptionPlan(
        Organization organization,
        String name,
        String code,
        BillingPeriod billingPeriod,
        BigDecimal price
    ) {
        this.organization =
            organization;

        this.name =
            name;

        this.code =
            code;

        this.billingPeriod =
            billingPeriod;

        this.price =
            price;
    }

    public UUID getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
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

    public boolean isActive() {
        return active;
    }

    public void setActive(
        boolean active
    ) {
        this.active =
            active;
    }

    public String getRules() {
        return rules;
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

    public QuotaPeriodType getQuotaPeriodType() {
        return quotaPeriodType;
    }

    public void setQuotaPeriodType(
        QuotaPeriodType quotaPeriodType
    ) {
        this.quotaPeriodType =
            quotaPeriodType;
    }

    public RenewalPolicy getRenewalPolicy() {
        return renewalPolicy;
    }

    public void setRenewalPolicy(
        RenewalPolicy renewalPolicy
    ) {
        this.renewalPolicy =
            renewalPolicy;
    }

    public SuspensionPolicy getSuspensionPolicy() {
        return suspensionPolicy;
    }

    public void setSuspensionPolicy(
        SuspensionPolicy suspensionPolicy
    ) {
        this.suspensionPolicy =
            suspensionPolicy;
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

    public AudienceType getAudienceType() {
        return audienceType;
    }

    public void setAudienceType(
        AudienceType audienceType
    ) {
        this.audienceType =
            audienceType;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
