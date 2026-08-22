package com.sup2i.food.inventory.domain;

import com.sup2i.food.common.domain.MeasurementUnit;
import com.sup2i.food.identity.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory_movements")
public class InventoryMovement {

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

    @Enumerated(EnumType.STRING)
    @Column(
        name = "movement_type",
        nullable = false,
        length = 40
    )
    private InventoryMovementType movementType;

    @Column(
        name = "physical_delta",
        nullable = false,
        precision = 14,
        scale = 3
    )
    private BigDecimal physicalDelta;

    @Column(
        name = "reserved_delta",
        nullable = false,
        precision = 14,
        scale = 3
    )
    private BigDecimal reservedDelta;

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
        scale = 2
    )
    private BigDecimal unitCost;

    @Column(
        name = "reference_type",
        length = 40
    )
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(
        name = "reason",
        length = 150
    )
    private String reason;

    @Column(
        name = "comment",
        columnDefinition = "TEXT"
    )
    private String comment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by")
    private User performedBy;

    @CreationTimestamp
    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private OffsetDateTime createdAt;

    protected InventoryMovement() {
    }

    public InventoryMovement(
        StockItem stockItem,
        StockLocation stockLocation,
        InventoryMovementType movementType,
        BigDecimal physicalDelta,
        BigDecimal reservedDelta,
        MeasurementUnit unit,
        BigDecimal unitCost,
        String referenceType,
        UUID referenceId,
        String reason,
        String comment,
        User performedBy
    ) {
        this.stockItem = stockItem;
        this.stockLocation = stockLocation;
        this.movementType = movementType;
        this.physicalDelta = physicalDelta;
        this.reservedDelta = reservedDelta;
        this.unit = unit;
        this.unitCost = unitCost;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.reason = reason;
        this.comment = comment;
        this.performedBy = performedBy;
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

    public InventoryMovementType getMovementType() {
        return movementType;
    }

    public BigDecimal getPhysicalDelta() {
        return physicalDelta;
    }

    public BigDecimal getReservedDelta() {
        return reservedDelta;
    }

    public MeasurementUnit getUnit() {
        return unit;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public String getReason() {
        return reason;
    }

    public String getComment() {
        return comment;
    }

    public User getPerformedBy() {
        return performedBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}