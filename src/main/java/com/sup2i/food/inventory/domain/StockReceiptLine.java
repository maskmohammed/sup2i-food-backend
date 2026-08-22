package com.sup2i.food.inventory.domain;

import com.sup2i.food.common.domain.MeasurementUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_receipt_lines")
public class StockReceiptLine {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "stock_receipt_id",
        nullable = false
    )
    private StockReceipt stockReceipt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "stock_item_id",
        nullable = false
    )
    private StockItem stockItem;

    @Column(
        name = "quantity",
        nullable = false,
        precision = 14,
        scale = 3
    )
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "unit",
        nullable = false,
        length = 20
    )
    private MeasurementUnit unit;

    @Column(
        name = "unit_cost",
        precision = 12,
        scale = 4
    )
    private BigDecimal unitCost;

    @Column(
        name = "lot_number",
        length = 120
    )
    private String lotNumber;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generated_lot_id")
    private StockLot generatedLot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_movement_id")
    private InventoryMovement inventoryMovement;

    protected StockReceiptLine() {
    }

    public StockReceiptLine(
        UUID id,
        StockReceipt stockReceipt,
        StockItem stockItem,
        BigDecimal quantity,
        MeasurementUnit unit,
        BigDecimal unitCost,
        String lotNumber,
        OffsetDateTime expiresAt,
        StockLot generatedLot,
        InventoryMovement inventoryMovement
    ) {
        this.id = id;
        this.stockReceipt =
            stockReceipt;
        this.stockItem =
            stockItem;
        this.quantity =
            quantity;
        this.unit = unit;
        this.unitCost =
            unitCost;
        this.lotNumber =
            lotNumber;
        this.expiresAt =
            expiresAt;
        this.generatedLot =
            generatedLot;
        this.inventoryMovement =
            inventoryMovement;
    }

    public UUID getId() {
        return id;
    }

    public StockReceipt getStockReceipt() {
        return stockReceipt;
    }

    public StockItem getStockItem() {
        return stockItem;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public MeasurementUnit getUnit() {
        return unit;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public String getLotNumber() {
        return lotNumber;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public StockLot getGeneratedLot() {
        return generatedLot;
    }

    public InventoryMovement getInventoryMovement() {
        return inventoryMovement;
    }
}