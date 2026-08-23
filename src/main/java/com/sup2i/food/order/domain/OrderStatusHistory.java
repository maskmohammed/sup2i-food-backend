package com.sup2i.food.order.domain;

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

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "order_status_history")
public class OrderStatusHistory {

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

    @Enumerated(EnumType.STRING)
    @Column(
        name = "from_status",
        length = 40
    )
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "to_status",
        nullable = false,
        length = 40
    )
    private OrderStatus toStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private User changedBy;

    @Column(
        name = "reason",
        columnDefinition = "text"
    )
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "source",
        nullable = false,
        length = 30
    )
    private OrderStatusHistorySource source;

    @CreationTimestamp
    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private OffsetDateTime createdAt;

    protected OrderStatusHistory() {
    }

    public OrderStatusHistory(
        Order order,
        OrderStatus fromStatus,
        OrderStatus toStatus,
        User changedBy,
        String reason,
        OrderStatusHistorySource source
    ) {
        this.order =
            order;

        this.fromStatus =
            fromStatus;

        this.toStatus =
            toStatus;

        this.changedBy =
            changedBy;

        this.reason =
            reason;

        this.source =
            source;
    }

    public UUID getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public OrderStatus getFromStatus() {
        return fromStatus;
    }

    public OrderStatus getToStatus() {
        return toStatus;
    }

    public User getChangedBy() {
        return changedBy;
    }

    public String getReason() {
        return reason;
    }

    public OrderStatusHistorySource getSource() {
        return source;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}