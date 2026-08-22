package com.sup2i.food.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "stock_balances")
public class StockBalance {

    @EmbeddedId
    private StockBalanceId id;

    @MapsId("stockItemId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "stock_item_id",
        nullable = false
    )
    private StockItem stockItem;

    @MapsId("stockLocationId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "stock_location_id",
        nullable = false
    )
    private StockLocation stockLocation;

    @Column(
        name = "physical_quantity",
        nullable = false,
        precision = 14,
        scale = 3
    )
    private BigDecimal physicalQuantity =
        BigDecimal.ZERO;

    @Column(
        name = "reserved_quantity",
        nullable = false,
        precision = 14,
        scale = 3
    )
    private BigDecimal reservedQuantity =
        BigDecimal.ZERO;

    @UpdateTimestamp
    @Column(
        name = "updated_at",
        nullable = false
    )
    private OffsetDateTime updatedAt;

    protected StockBalance() {
    }

    public StockBalance(
        StockItem stockItem,
        StockLocation stockLocation
    ) {
        this.id =
            new StockBalanceId(
                stockItem.getId(),
                stockLocation.getId()
            );

        this.stockItem = stockItem;
        this.stockLocation = stockLocation;
    }

    public StockBalanceId getId() {
        return id;
    }

    public StockItem getStockItem() {
        return stockItem;
    }

    public StockLocation getStockLocation() {
        return stockLocation;
    }

    public BigDecimal getPhysicalQuantity() {
        return physicalQuantity;
    }

    public BigDecimal getReservedQuantity() {
        return reservedQuantity;
    }

    public BigDecimal getAvailableQuantity() {

        return physicalQuantity.subtract(
            reservedQuantity
        );
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void applyPhysicalDelta(
        BigDecimal delta
    ) {
        physicalQuantity =
            physicalQuantity.add(delta);
    }
}