package com.sup2i.food.kitchen.domain;

import com.sup2i.food.order.domain.OrderItem;
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
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "kitchen_ticket_items")
public class KitchenTicketItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "kitchen_ticket_id",
        nullable = false
    )
    private KitchenTicket kitchenTicket;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "order_item_id",
        nullable = false
    )
    private OrderItem orderItem;

    @Column(name = "menu_selection_id")
    private UUID menuSelectionId;

    @Column(
        nullable = false,
        precision = 14,
        scale = 3
    )
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(
        nullable = false,
        length = 30
    )
    private KitchenTicketItemStatus status =
        KitchenTicketItemStatus.QUEUED;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "ready_at")
    private OffsetDateTime readyAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(
        name = "issue_note",
        columnDefinition = "TEXT"
    )
    private String issueNote;

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

    protected KitchenTicketItem() {
    }

    public KitchenTicketItem(
        KitchenTicket kitchenTicket,
        OrderItem orderItem,
        UUID menuSelectionId,
        BigDecimal quantity
    ) {
        this.kitchenTicket = kitchenTicket;
        this.orderItem = orderItem;
        this.menuSelectionId = menuSelectionId;
        this.quantity = quantity;
    }

    public UUID getId() {
        return id;
    }

    public KitchenTicket getKitchenTicket() {
        return kitchenTicket;
    }

    public OrderItem getOrderItem() {
        return orderItem;
    }

    public UUID getMenuSelectionId() {
        return menuSelectionId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public KitchenTicketItemStatus getStatus() {
        return status;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getReadyAt() {
        return readyAt;
    }

    public OffsetDateTime getCancelledAt() {
        return cancelledAt;
    }

    public String getIssueNote() {
        return issueNote;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void markPreparing(
        OffsetDateTime at
    ) {
        if (
            status
                != KitchenTicketItemStatus.QUEUED
        ) {
            throw new IllegalStateException(
                "Only a queued kitchen ticket item can start."
            );
        }

        startedAt = at;
        status = KitchenTicketItemStatus.PREPARING;
    }

    public void markReady(
        OffsetDateTime at
    ) {
        if (
            status
                != KitchenTicketItemStatus.PREPARING
        ) {
            throw new IllegalStateException(
                "Only a preparing kitchen ticket item can become ready."
            );
        }

        readyAt = at;
        status = KitchenTicketItemStatus.READY;
    }

    public void cancel(
        OffsetDateTime at,
        String issueNote
    ) {
        boolean terminal =
            status == KitchenTicketItemStatus.READY
            || status == KitchenTicketItemStatus.CANCELLED;

        if (terminal) {
            throw new IllegalStateException(
                "Kitchen ticket item can no longer be cancelled."
            );
        }

        cancelledAt = at;
        this.issueNote = issueNote;
        status = KitchenTicketItemStatus.CANCELLED;
    }
}