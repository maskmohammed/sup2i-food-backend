package com.sup2i.food.kitchen.domain;

import com.sup2i.food.identity.domain.User;
import com.sup2i.food.order.domain.Order;
import com.sup2i.food.organization.domain.Location;
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
@Table(name = "kitchen_tickets")
public class KitchenTicket {

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
        name = "order_id",
        nullable = false
    )
    private Order order;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "kitchen_location_id",
        nullable = false
    )
    private Location kitchenLocation;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 30
    )
    private KitchenTicketStatus status;

    @Column(
        name = "priority",
        nullable = false
    )
    private int priority;

    @CreationTimestamp
    @Column(
        name = "queued_at",
        nullable = false,
        updatable = false
    )
    private OffsetDateTime queuedAt;

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "ready_at")
    private OffsetDateTime readyAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    protected KitchenTicket() {
    }

    public KitchenTicket(
        Order order,
        Location kitchenLocation
    ) {
        this.order =
            order;

        this.kitchenLocation =
            kitchenLocation;

        this.status =
            KitchenTicketStatus.QUEUED;

        this.priority =
            0;
    }

    public UUID getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public Location getKitchenLocation() {
        return kitchenLocation;
    }

    public KitchenTicketStatus getStatus() {
        return status;
    }

    public int getPriority() {
        return priority;
    }

    public OffsetDateTime getQueuedAt() {
        return queuedAt;
    }

    public OffsetDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getReadyAt() {
        return readyAt;
    }

    public User getAssignedTo() {
        return assignedTo;
    }

    public void startPreparation(
        User actor,
        OffsetDateTime at
    ) {
        status =
            KitchenTicketStatus.PREPARING;

        acceptedAt =
            at;

        startedAt =
            at;

        assignedTo =
            actor;
    }

    public void markReady(
        OffsetDateTime at
    ) {
        status =
            KitchenTicketStatus.READY;

        readyAt =
            at;
    }
}
