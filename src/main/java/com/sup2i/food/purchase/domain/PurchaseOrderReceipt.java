package com.sup2i.food.purchase.domain;

import com.sup2i.food.identity.domain.User;
import com.sup2i.food.inventory.domain.StockLocation;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_receipts")
public class PurchaseOrderReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "purchase_order_id",
        nullable = false
    )
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "stock_location_id",
        nullable = false
    )
    private StockLocation stockLocation;

    @CreationTimestamp
    @Column(
        name = "received_at",
        nullable = false,
        updatable = false
    )
    private OffsetDateTime receivedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "received_by",
        nullable = false
    )
    private User receivedBy;

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

    @UpdateTimestamp
    @Column(
        name = "updated_at",
        nullable = false
    )
    private OffsetDateTime updatedAt;

    @OneToMany(
        mappedBy = "receipt",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    @OrderBy("id asc")
    private List<PurchaseOrderReceiptLine> lines =
        new ArrayList<>();

    protected PurchaseOrderReceipt() {
    }

    public PurchaseOrderReceipt(
        PurchaseOrder purchaseOrder,
        StockLocation stockLocation,
        User receivedBy,
        String notes
    ) {
        this.purchaseOrder = purchaseOrder;
        this.stockLocation = stockLocation;
        this.receivedBy = receivedBy;
        this.notes = notes;
    }

    public void addLine(
        PurchaseOrderReceiptLine line
    ) {
        lines.add(line);
    }

    public UUID getId() {
        return id;
    }

    public PurchaseOrder getPurchaseOrder() {
        return purchaseOrder;
    }

    public StockLocation getStockLocation() {
        return stockLocation;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public User getReceivedBy() {
        return receivedBy;
    }

    public String getNotes() {
        return notes;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<PurchaseOrderReceiptLine> getLines() {
        return Collections.unmodifiableList(
            lines
        );
    }
}