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
@Table(name = "food_pass_events")
public class FoodPassEvent {

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
        name = "food_pass_id",
        nullable = false
    )
    private FoodPass foodPass;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "event_type",
        nullable = false,
        length = 40
    )
    private FoodPassEventType eventType;

    @Column(
        name = "reason",
        columnDefinition = "text"
    )
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by")
    private User performedBy;

    @CreationTimestamp
    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private OffsetDateTime createdAt;

    protected FoodPassEvent() {
    }

    public FoodPassEvent(
        FoodPass foodPass,
        FoodPassEventType eventType,
        String reason,
        User performedBy
    ) {
        this.foodPass =
            foodPass;

        this.eventType =
            eventType;

        this.reason =
            reason;

        this.performedBy =
            performedBy;
    }

    public UUID getId() {
        return id;
    }

    public FoodPass getFoodPass() {
        return foodPass;
    }

    public FoodPassEventType getEventType() {
        return eventType;
    }

    public String getReason() {
        return reason;
    }

    public User getPerformedBy() {
        return performedBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}