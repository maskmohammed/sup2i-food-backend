package com.sup2i.food.promotion.domain;

import com.sup2i.food.identity.domain.User;
import com.sup2i.food.order.domain.Order;
import com.sup2i.food.order.domain.OrderItem;
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
@Table(name = "order_discounts")
public class OrderDiscount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id")
    private OrderItem orderItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private DiscountSourceType sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "code_snapshot", length = 100)
    private String codeSnapshot;

    @Column(name = "label_snapshot", nullable = false, length = 180)
    private String labelSnapshot;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manually_applied_by")
    private User manuallyAppliedBy;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected OrderDiscount() {
    }

    public OrderDiscount(
        Order order,
        DiscountSourceType sourceType,
        UUID sourceId,
        String codeSnapshot,
        String labelSnapshot,
        BigDecimal amount,
        String reason
    ) {
        this.order = order;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.codeSnapshot = codeSnapshot;
        this.labelSnapshot = labelSnapshot;
        this.amount = amount;
        this.reason = reason;
    }

    public UUID getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public DiscountSourceType getSourceType() {
        return sourceType;
    }

    public UUID getSourceId() {
        return sourceId;
    }
}