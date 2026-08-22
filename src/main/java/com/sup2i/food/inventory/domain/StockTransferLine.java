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
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
    name = "stock_transfer_lines",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_stock_transfer_line",
            columnNames = {
                "stock_transfer_id",
                "stock_item_id"
            }
        )
    }
)
public class StockTransferLine {

    @Id
    private UUID id;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "stock_transfer_id",
        nullable = false
    )
    private StockTransfer stockTransfer;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "transfer_out_movement_id"
    )
    private InventoryMovement transferOutMovement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "transfer_in_movement_id"
    )
    private InventoryMovement transferInMovement;

    protected StockTransferLine() {
    }

    public StockTransferLine(
        UUID id,
        StockTransfer stockTransfer,
        StockItem stockItem,
        BigDecimal quantity,
        MeasurementUnit unit
    ) {
        this.id =
            id;

        this.stockTransfer =
            stockTransfer;

        this.stockItem =
            stockItem;

        this.quantity =
            quantity;

        this.unit =
            unit;
    }

    public UUID getId() {
        return id;
    }

    public StockTransfer getStockTransfer() {
        return stockTransfer;
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

    public InventoryMovement getTransferOutMovement() {
        return transferOutMovement;
    }

    public InventoryMovement getTransferInMovement() {
        return transferInMovement;
    }

    public void attachTransferOutMovement(
        InventoryMovement movement
    ) {
        transferOutMovement =
            movement;
    }

    public void attachTransferInMovement(
        InventoryMovement movement
    ) {
        transferInMovement =
            movement;
    }
}