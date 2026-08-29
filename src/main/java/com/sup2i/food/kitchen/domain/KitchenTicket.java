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
    @GeneratedValue(strategy = GenerationType.UUID)
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
        nullable = false,
        length = 30
    )
    private KitchenTicketStatus status =
        KitchenTicketStatus.QUEUED;

    @Column(
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preparation_route_id")
    private PreparationRoute preparationRoute;

    protected KitchenTicket() {
    }

    public KitchenTicket(
        Order order,
        Location kitchenLocation,
        PreparationRoute preparationRoute,
        int priority
    ) {
        this.order = order;
        this.kitchenLocation = kitchenLocation;
        this.preparationRoute = preparationRoute;
        this.priority = priority;
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

    public PreparationRoute getPreparationRoute() {
        return preparationRoute;
    }

    public void markPreparing(
        User actor,
        OffsetDateTime at
    ) {
        boolean startable =
            status == KitchenTicketStatus.QUEUED
            || status == KitchenTicketStatus.ACCEPTED;

        if (!startable) {
            throw new IllegalStateException(
                "Only a queued or accepted kitchen ticket can start."
            );
        }

        if (acceptedAt == null) {
            acceptedAt = at;
        }

        assignedTo = actor;
        startedAt = at;
        status = KitchenTicketStatus.PREPARING;
    }

    public void markReady(
        OffsetDateTime at
    ) {
        if (
            status
                != KitchenTicketStatus.PREPARING
        ) {
            throw new IllegalStateException(
                "Only a preparing kitchen ticket can become ready."
            );
        }

        readyAt = at;
        status = KitchenTicketStatus.READY;
    }

    public void cancel() {
        boolean terminal =
            status == KitchenTicketStatus.READY
            || status == KitchenTicketStatus.CANCELLED;

        if (terminal) {
            throw new IllegalStateException(
                "Kitchen ticket can no longer be cancelled."
            );
        }

        status = KitchenTicketStatus.CANCELLED;
    }
}