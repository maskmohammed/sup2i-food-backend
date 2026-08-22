package com.sup2i.food.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class StockBalanceId
    implements Serializable {

    @Column(name = "stock_item_id")
    private UUID stockItemId;

    @Column(name = "stock_location_id")
    private UUID stockLocationId;

    protected StockBalanceId() {
    }

    public StockBalanceId(
        UUID stockItemId,
        UUID stockLocationId
    ) {
        this.stockItemId = stockItemId;
        this.stockLocationId =
            stockLocationId;
    }

    public UUID getStockItemId() {
        return stockItemId;
    }

    public UUID getStockLocationId() {
        return stockLocationId;
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

        StockBalanceId that =
            (StockBalanceId) object;

        return Objects.equals(
                stockItemId,
                that.stockItemId
            )
            && Objects.equals(
                stockLocationId,
                that.stockLocationId
            );
    }

    @Override
    public int hashCode() {

        return Objects.hash(
            stockItemId,
            stockLocationId
        );
    }
}