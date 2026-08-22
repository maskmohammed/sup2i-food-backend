package com.sup2i.food.inventory.domain;

import com.sup2i.food.identity.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "inventory_count_lines",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_inventory_count_line",
            columnNames = {
                "inventory_session_id",
                "stock_item_id"
            }
        )
    }
)
public class InventoryCountLine {

    @Id
    @GeneratedValue(
        strategy = GenerationType.UUID
    )
    private UUID id;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "inventory_session_id",
        nullable = false
    )
    private InventorySession inventorySession;

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
        name = "system_physical_quantity",
        nullable = false,
        precision = 14,
        scale = 3
    )
    private BigDecimal systemPhysicalQuantity;

    @Column(
        name = "system_reserved_quantity",
        nullable = false,
        precision = 14,
        scale = 3
    )
    private BigDecimal systemReservedQuantity;

    @Column(
        name = "counted_quantity",
        precision = 14,
        scale = 3
    )
    private BigDecimal countedQuantity;

    @Column(
        name = "difference_quantity",
        precision = 14,
        scale = 3
    )
    private BigDecimal differenceQuantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counted_by")
    private User countedBy;

    @Column(name = "counted_at")
    private OffsetDateTime countedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "adjustment_movement_id"
    )
    private InventoryMovement adjustmentMovement;

    @Column(
        name = "reason",
        columnDefinition = "TEXT"
    )
    private String reason;

    protected InventoryCountLine() {
    }

    public InventoryCountLine(
        InventorySession inventorySession,
        StockItem stockItem,
        BigDecimal systemPhysicalQuantity,
        BigDecimal systemReservedQuantity
    ) {
        this.inventorySession =
            inventorySession;
        this.stockItem =
            stockItem;
        this.systemPhysicalQuantity =
            systemPhysicalQuantity;
        this.systemReservedQuantity =
            systemReservedQuantity;
    }

    public UUID getId() {
        return id;
    }

    public InventorySession getInventorySession() {
        return inventorySession;
    }

    public StockItem getStockItem() {
        return stockItem;
    }

    public BigDecimal getSystemPhysicalQuantity() {
        return systemPhysicalQuantity;
    }

    public BigDecimal getSystemReservedQuantity() {
        return systemReservedQuantity;
    }

    public BigDecimal getCountedQuantity() {
        return countedQuantity;
    }

    public BigDecimal getDifferenceQuantity() {
        return differenceQuantity;
    }

    public User getCountedBy() {
        return countedBy;
    }

    public OffsetDateTime getCountedAt() {
        return countedAt;
    }

    public InventoryMovement getAdjustmentMovement() {
        return adjustmentMovement;
    }

    public String getReason() {
        return reason;
    }

    public void count(
        BigDecimal countedQuantity,
        User countedBy,
        String reason
    ) {
        this.countedQuantity =
            countedQuantity;

        this.differenceQuantity =
            countedQuantity.subtract(
                systemPhysicalQuantity
            );

        this.countedBy =
            countedBy;

        this.countedAt =
            OffsetDateTime.now();

        this.reason =
            reason;
    }

    public void attachAdjustment(
        InventoryMovement movement
    ) {
        this.adjustmentMovement =
            movement;
    }
}