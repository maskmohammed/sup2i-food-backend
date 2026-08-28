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

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "subscription_status_history")
public class SubscriptionStatusHistory {

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
        name = "from_status",
        length = 30
    )
    private SubscriptionStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "to_status",
        nullable = false,
        length = 30
    )
    private SubscriptionStatus toStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private User changedBy;

    @Column(
        name = "reason",
        columnDefinition = "text"
    )
    private String reason;

    @CreationTimestamp
    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private OffsetDateTime createdAt;

    protected SubscriptionStatusHistory() {
    }

    public SubscriptionStatusHistory(
        Subscription subscription,
        SubscriptionStatus fromStatus,
        SubscriptionStatus toStatus,
        User changedBy,
        String reason
    ) {
        this.subscription =
            subscription;

        this.fromStatus =
            fromStatus;

        this.toStatus =
            toStatus;

        this.changedBy =
            changedBy;

        this.reason =
            reason;
    }

    public UUID getId() {
        return id;
    }

    public Subscription getSubscription() {
        return subscription;
    }

    public SubscriptionStatus getFromStatus() {
        return fromStatus;
    }

    public SubscriptionStatus getToStatus() {
        return toStatus;
    }

    public User getChangedBy() {
        return changedBy;
    }

    public String getReason() {
        return reason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
