package com.sup2i.food.inventory.domain;

import com.sup2i.food.identity.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_transfers")
public class StockTransfer {

    @Id
    private UUID id;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "source_stock_location_id",
        nullable = false
    )
    private StockLocation sourceStockLocation;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "destination_stock_location_id",
        nullable = false
    )
    private StockLocation destinationStockLocation;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 30
    )
    private StockTransferStatus status;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "requested_by",
        nullable = false
    )
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispatched_by")
    private User dispatchedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by")
    private User receivedBy;

    @Column(
        name = "requested_at",
        nullable = false
    )
    private OffsetDateTime requestedAt;

    @Column(name = "dispatched_at")
    private OffsetDateTime dispatchedAt;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(
        name = "reason",
        columnDefinition = "TEXT"
    )
    private String reason;

    protected StockTransfer() {
    }

    public StockTransfer(
        UUID id,
        StockLocation sourceStockLocation,
        StockLocation destinationStockLocation,
        User requestedBy,
        String reason
    ) {
        this.id =
            id;

        this.sourceStockLocation =
            sourceStockLocation;

        this.destinationStockLocation =
            destinationStockLocation;

        this.status =
            StockTransferStatus.DRAFT;

        this.requestedBy =
            requestedBy;

        this.requestedAt =
            OffsetDateTime.now();

        this.reason =
            reason;
    }

    public UUID getId() {
        return id;
    }

    public StockLocation getSourceStockLocation() {
        return sourceStockLocation;
    }

    public StockLocation getDestinationStockLocation() {
        return destinationStockLocation;
    }

    public StockTransferStatus getStatus() {
        return status;
    }

    public User getRequestedBy() {
        return requestedBy;
    }

    public User getApprovedBy() {
        return approvedBy;
    }

    public User getDispatchedBy() {
        return dispatchedBy;
    }

    public User getReceivedBy() {
        return receivedBy;
    }

    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    public OffsetDateTime getDispatchedAt() {
        return dispatchedAt;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public OffsetDateTime getCancelledAt() {
        return cancelledAt;
    }

    public String getReason() {
        return reason;
    }

    public void revise(
        StockLocation source,
        StockLocation destination,
        String reason
    ) {

        if (
            status
                != StockTransferStatus.DRAFT
        ) {
            throw new IllegalStateException(
                "Only draft transfers are editable."
            );
        }

        sourceStockLocation =
            source;

        destinationStockLocation =
            destination;

        this.reason =
            reason;
    }

    public void approve(
        User actor
    ) {
        status =
            StockTransferStatus.APPROVED;

        approvedBy =
            actor;
    }

    public void dispatch(
        User actor
    ) {
        status =
            StockTransferStatus.IN_TRANSIT;

        dispatchedBy =
            actor;

        dispatchedAt =
            OffsetDateTime.now();
    }

    public void receive(
        User actor
    ) {
        status =
            StockTransferStatus.RECEIVED;

        receivedBy =
            actor;

        receivedAt =
            OffsetDateTime.now();
    }

    public void cancel() {
        status =
            StockTransferStatus.CANCELLED;

        cancelledAt =
            OffsetDateTime.now();
    }
}