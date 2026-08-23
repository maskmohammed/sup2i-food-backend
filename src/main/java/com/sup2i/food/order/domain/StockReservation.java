package com.sup2i.food.order.domain;

import com.sup2i.food.inventory.domain.StockItem;
import com.sup2i.food.inventory.domain.StockLocation;
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
@Table(name = "stock_reservations")
public class StockReservation {

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
        name = "order_id",
        nullable = false
    )
    private Order order;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "stock_item_id",
        nullable = false
    )
    private StockItem stockItem;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "stock_location_id",
        nullable = false
    )
    private StockLocation stockLocation;

    @Column(
        name = "quantity",
        nullable = false,
        precision = 14,
        scale = 3
    )
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 30
    )
    private StockReservationStatus status;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @CreationTimestamp
    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private OffsetDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id")
    private OrderItem orderItem;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    @Column(name = "released_at")
    private OffsetDateTime releasedAt;

    protected StockReservation() {
    }

    public StockReservation(
        Order order,
        OrderItem orderItem,
        StockItem stockItem,
        StockLocation stockLocation,
        BigDecimal quantity,
        OffsetDateTime expiresAt
    ) {
        this.order =
            order;

        this.orderItem =
            orderItem;

        this.stockItem =
            stockItem;

        this.stockLocation =
            stockLocation;

        this.quantity =
            quantity;

        this.expiresAt =
            expiresAt;

        this.status =
            StockReservationStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public OrderItem getOrderItem() {
        return orderItem;
    }

    public StockItem getStockItem() {
        return stockItem;
    }

    public StockLocation getStockLocation() {
        return stockLocation;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public StockReservationStatus getStatus() {
        return status;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getConsumedAt() {
        return consumedAt;
    }

    public OffsetDateTime getReleasedAt() {
        return releasedAt;
    }

    public void release(
        OffsetDateTime at
    ) {
        status =
            StockReservationStatus.RELEASED;

        releasedAt =
            at;
    }

    public void expire(
        OffsetDateTime at
    ) {
        status =
            StockReservationStatus.EXPIRED;

        releasedAt =
            at;
    }

    public void consume(
        OffsetDateTime at
    ) {
        status =
            StockReservationStatus.CONSUMED;

        consumedAt =
            at;
    }
}