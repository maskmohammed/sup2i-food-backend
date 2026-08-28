package com.sup2i.food.purchase.domain;

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
@Table(name = "purchase_order_history")
public class PurchaseOrderHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "purchase_order_id",
        nullable = false
    )
    private PurchaseOrder purchaseOrder;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "event_type",
        nullable = false,
        length = 30
    )
    private PurchaseOrderHistoryEvent eventType;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status_before",
        length = 30
    )
    private PurchaseOrderStatus statusBefore;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status_after",
        nullable = false,
        length = 30
    )
    private PurchaseOrderStatus statusAfter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "performed_by",
        nullable = false
    )
    private User performedBy;

    @CreationTimestamp
    @Column(
        name = "occurred_at",
        nullable = false,
        updatable = false
    )
    private OffsetDateTime occurredAt;

    @Column(
        name = "notes",
        columnDefinition = "TEXT"
    )
    private String notes;

    protected PurchaseOrderHistory() {
    }

    public PurchaseOrderHistory(
        PurchaseOrder purchaseOrder,
        PurchaseOrderHistoryEvent eventType,
        PurchaseOrderStatus statusBefore,
        PurchaseOrderStatus statusAfter,
        User performedBy,
        String notes
    ) {
        this.purchaseOrder = purchaseOrder;
        this.eventType = eventType;
        this.statusBefore = statusBefore;
        this.statusAfter = statusAfter;
        this.performedBy = performedBy;
        this.notes = notes;
    }

    public UUID getId() {
        return id;
    }

    public PurchaseOrder getPurchaseOrder() {
        return purchaseOrder;
    }

    public PurchaseOrderHistoryEvent getEventType() {
        return eventType;
    }

    public PurchaseOrderStatus getStatusBefore() {
        return statusBefore;
    }

    public PurchaseOrderStatus getStatusAfter() {
        return statusAfter;
    }

    public User getPerformedBy() {
        return performedBy;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public String getNotes() {
        return notes;
    }
}