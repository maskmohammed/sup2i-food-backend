package com.sup2i.food.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "inventory_movement_lots")
public class InventoryMovementLot {

    @EmbeddedId
    private InventoryMovementLotId id;

    @MapsId("inventoryMovementId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "inventory_movement_id",
        nullable = false
    )
    private InventoryMovement movement;

    @MapsId("stockLotId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "stock_lot_id",
        nullable = false
    )
    private StockLot stockLot;

    @Column(
        name = "quantity_delta",
        nullable = false,
        precision = 14,
        scale = 3
    )
    private BigDecimal quantityDelta;

    protected InventoryMovementLot() {
    }

    public InventoryMovementLot(
        InventoryMovement movement,
        StockLot stockLot,
        BigDecimal quantityDelta
    ) {
        this.id =
            new InventoryMovementLotId(
                movement.getId(),
                stockLot.getId()
            );

        this.movement = movement;
        this.stockLot = stockLot;
        this.quantityDelta =
            quantityDelta;
    }

    public InventoryMovementLotId getId() {
        return id;
    }

    public InventoryMovement getMovement() {
        return movement;
    }

    public StockLot getStockLot() {
        return stockLot;
    }

    public BigDecimal getQuantityDelta() {
        return quantityDelta;
    }
}