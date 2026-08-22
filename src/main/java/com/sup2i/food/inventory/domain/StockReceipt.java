package com.sup2i.food.inventory.domain;

import com.sup2i.food.identity.domain.User;
import com.sup2i.food.procurement.domain.Supplier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_receipts")
public class StockReceipt {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "stock_location_id",
        nullable = false
    )
    private StockLocation stockLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(
        name = "receipt_reference",
        length = 100
    )
    private String receiptReference;

    @Column(
        name = "received_at",
        nullable = false
    )
    private OffsetDateTime receivedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "received_by",
        nullable = false
    )
    private User receivedBy;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 30
    )
    private StockReceiptStatus status;

    @Column(
        name = "notes",
        columnDefinition = "TEXT"
    )
    private String notes;

    @CreationTimestamp
    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private OffsetDateTime createdAt;

    protected StockReceipt() {
    }

    public StockReceipt(
        UUID id,
        StockLocation stockLocation,
        Supplier supplier,
        String receiptReference,
        OffsetDateTime receivedAt,
        User receivedBy,
        String notes
    ) {
        this.id = id;
        this.stockLocation =
            stockLocation;
        this.supplier = supplier;
        this.receiptReference =
            receiptReference;
        this.receivedAt = receivedAt;
        this.receivedBy = receivedBy;
        this.status =
            StockReceiptStatus.RECEIVED;
        this.notes = notes;
    }

    public UUID getId() {
        return id;
    }

    public StockLocation getStockLocation() {
        return stockLocation;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public String getReceiptReference() {
        return receiptReference;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public User getReceivedBy() {
        return receivedBy;
    }

    public StockReceiptStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}