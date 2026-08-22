package com.sup2i.food.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class InventoryMovementLotId
    implements Serializable {

    @Column(name = "inventory_movement_id")
    private UUID inventoryMovementId;

    @Column(name = "stock_lot_id")
    private UUID stockLotId;

    protected InventoryMovementLotId() {
    }

    public InventoryMovementLotId(
        UUID inventoryMovementId,
        UUID stockLotId
    ) {
        this.inventoryMovementId =
            inventoryMovementId;

        this.stockLotId =
            stockLotId;
    }

    public UUID getInventoryMovementId() {
        return inventoryMovementId;
    }

    public UUID getStockLotId() {
        return stockLotId;
    }

    @Override
    public boolean equals(
        Object object
    ) {

        if (this == object) {
            return true;
        }

        if (
            object == null
            || getClass() != object.getClass()
        ) {
            return false;
        }

        InventoryMovementLotId that =
            (InventoryMovementLotId) object;

        return Objects.equals(
                inventoryMovementId,
                that.inventoryMovementId
            )
            && Objects.equals(
                stockLotId,
                that.stockLotId
            );
    }

    @Override
    public int hashCode() {

        return Objects.hash(
            inventoryMovementId,
            stockLotId
        );
    }
}