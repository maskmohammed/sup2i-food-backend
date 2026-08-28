package com.sup2i.food.subscription.domain;

import com.sup2i.food.identity.domain.Student;
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
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(
        strategy = GenerationType.UUID
    )
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "plan_id",
        nullable = false
    )
    private SubscriptionPlan plan;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "plan_version_id",
        nullable = false
    )
    private SubscriptionPlanVersion planVersion;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 30
    )
    private SubscriptionStatus status =
        SubscriptionStatus.PENDING;

    @Column(
        name = "starts_at",
        nullable = false
    )
    private LocalDate startsAt;

    @Column(
        name = "ends_at",
        nullable = false
    )
    private LocalDate endsAt;

    @Column(
        name = "payment_reference",
        length = 160
    )
    private String paymentReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activated_by")
    private User activatedBy;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    @Column(name = "suspended_at")
    private OffsetDateTime suspendedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "renewed_from_id")
    private Subscription renewedFrom;

    @Column(
        name = "administrative_payment_amount",
        precision = 12,
        scale = 2
    )
    private BigDecimal administrativePaymentAmount;

    /*
     * Formules personnel/evenement (V057) : hors perimetre MVP.
     * Toujours NULL pour ce module, le chemin student_id est le seul
     * implemente. La contrainte DB num_nonnulls(student_id,
     * meal_beneficiary_id)=1 est donc toujours satisfaite via student_id.
     */
    @Column(name = "meal_beneficiary_id")
    private UUID mealBeneficiaryId;

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

    protected Subscription() {
    }

    public Subscription(
        Student student,
        SubscriptionPlan plan,
        SubscriptionPlanVersion planVersion,
        LocalDate startsAt,
        LocalDate endsAt
    ) {
        this.student =
            student;

        this.plan =
            plan;

        this.planVersion =
            planVersion;

        this.startsAt =
            startsAt;

        this.endsAt =
            endsAt;
    }

    public UUID getId() {
        return id;
    }

    public Student getStudent() {
        return student;
    }

    public SubscriptionPlan getPlan() {
        return plan;
    }

    public SubscriptionPlanVersion getPlanVersion() {
        return planVersion;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public LocalDate getStartsAt() {
        return startsAt;
    }

    public LocalDate getEndsAt() {
        return endsAt;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public User getActivatedBy() {
        return activatedBy;
    }

    public OffsetDateTime getActivatedAt() {
        return activatedAt;
    }

    public OffsetDateTime getSuspendedAt() {
        return suspendedAt;
    }

    public OffsetDateTime getCancelledAt() {
        return cancelledAt;
    }

    public Subscription getRenewedFrom() {
        return renewedFrom;
    }

    public BigDecimal
        getAdministrativePaymentAmount() {
        return administrativePaymentAmount;
    }

    public UUID getMealBeneficiaryId() {
        return mealBeneficiaryId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void activate(
        User activatedBy,
        OffsetDateTime activatedAt,
        String paymentReference,
        BigDecimal administrativePaymentAmount
    ) {
        this.status =
            SubscriptionStatus.ACTIVE;

        this.activatedBy =
            activatedBy;

        this.activatedAt =
            activatedAt;

        this.paymentReference =
            paymentReference;

        this.administrativePaymentAmount =
            administrativePaymentAmount;
    }

    public void suspend(
        OffsetDateTime suspendedAt
    ) {
        this.status =
            SubscriptionStatus.SUSPENDED;

        this.suspendedAt =
            suspendedAt;
    }

    public void reactivate() {
        this.status =
            SubscriptionStatus.ACTIVE;

        this.suspendedAt =
            null;
    }

    public void cancel(
        OffsetDateTime cancelledAt
    ) {
        this.status =
            SubscriptionStatus.CANCELLED;

        this.cancelledAt =
            cancelledAt;
    }

    public void expire() {
        this.status =
            SubscriptionStatus.EXPIRED;
    }
}
