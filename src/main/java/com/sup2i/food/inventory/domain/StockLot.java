package com.sup2i.food.inventory.domain;

import com.sup2i.food.procurement.domain.Supplier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_lots")
public class StockLot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "stock_item_id",
        nullable = false
    )
    private StockItem stockItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "stock_location_id",
        nullable = false
    )
    private StockLocation stockLocation;

    @Column(
        name = "lot_number",
        length = 120
    )
    private String lotNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(
        name = "received_at",
        nullable = false
    )
    private OffsetDateTime receivedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(
        name = "quantity_received",
        nullable = false,
        precision = 14,
        scale = 3
    )
    private BigDecimal quantityReceived;

    @Column(
        name = "quantity_remaining",
        nullable = false,
        precision = 14,
        scale = 3
    )
    private BigDecimal quantityRemaining;

    @Column(
        name = "unit_cost",
        precision = 12,
        scale = 2
    )
    private BigDecimal unitCost;

    protected StockLot() {
    }

    public StockLot(
        StockItem stockItem,
        StockLocation stockLocation,
        String lotNumber,
        Supplier supplier,
        OffsetDateTime receivedAt,
        OffsetDateTime expiresAt,
        BigDecimal quantityReceived,
        BigDecimal unitCost
    ) {
        this.stockItem = stockItem;
        this.stockLocation = stockLocation;
        this.lotNumber = lotNumber;
        this.supplier = supplier;
        this.receivedAt = receivedAt;
        this.expiresAt = expiresAt;
        this.quantityReceived =
            quantityReceived;
        this.quantityRemaining =
            quantityReceived;
        this.unitCost = unitCost;
    }

    public UUID getId() {
        return id;
    }

    public StockItem getStockItem() {
        return stockItem;
    }

    public StockLocation getStockLocation() {
        return stockLocation;
    }

    public String getLotNumber() {
        return lotNumber;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public BigDecimal getQuantityReceived() {
        return quantityReceived;
    }

    public BigDecimal getQuantityRemaining() {
        return quantityRemaining;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public void consume(
        BigDecimal quantity
    ) {

        if (
            quantity == null
            || quantity.signum() <= 0
        ) {
            throw new IllegalArgumentException(
                "Lot consumption must be positive."
            );
        }

        if (
            quantity.compareTo(
                quantityRemaining
            ) > 0
        ) {
            throw new IllegalArgumentException(
                "Lot does not contain enough remaining quantity."
            );
        }

        quantityRemaining =
            quantityRemaining.subtract(
                quantity
            );
    }
}