package com.sup2i.food.payment.domain;

import com.sup2i.food.identity.domain.User;
import com.sup2i.food.order.domain.Order;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

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

    @Column(name = "pos_session_id")
    private UUID posSessionId;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "method",
        nullable = false,
        length = 30
    )
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 40
    )
    private PaymentStatus status;

    @Column(
        name = "amount",
        nullable = false,
        precision = 12,
        scale = 2
    )
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(
        name = "currency",
        nullable = false,
        length = 3,
        columnDefinition = "char(3)"
    )
    private String currency;

    @Column(
        name = "external_reference",
        length = 160
    )
    private String externalReference;

    @Column(
        name = "idempotency_key",
        length = 160
    )
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by")
    private User receivedBy;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "reversed_at")
    private OffsetDateTime reversedAt;

    @CreationTimestamp
    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(
        name = "updated_at",
        nullable = false
    )
    private OffsetDateTime updatedAt;

    @Column(
        name = "tendered_amount",
        precision = 12,
        scale = 2
    )
    private BigDecimal tenderedAmount;

    @Column(
        name = "change_amount",
        precision = 12,
        scale = 2
    )
    private BigDecimal changeAmount;

    @Column(
        name = "provider_code",
        length = 80
    )
    private String providerCode;

    @Column(
        name = "failure_code",
        length = 100
    )
    private String failureCode;

    @Column(
        name = "failure_message",
        columnDefinition = "text"
    )
    private String failureMessage;

    protected Payment() {
    }

    public Payment(
        Order order,
        UUID posSessionId,
        PaymentMethod method,
        BigDecimal amount,
        String currency,
        String externalReference,
        String idempotencyKey,
        User receivedBy
    ) {
        this.order =
            order;

        this.posSessionId =
            posSessionId;

        this.method =
            method;

        this.amount =
            amount;

        this.currency =
            currency;

        this.externalReference =
            externalReference;

        this.idempotencyKey =
            idempotencyKey;

        this.receivedBy =
            receivedBy;

        this.status =
            PaymentStatus.PENDING;
    }

    public UUID getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public UUID getPosSessionId() {
        return posSessionId;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public User getReceivedBy() {
        return receivedBy;
    }

    public OffsetDateTime getPaidAt() {
        return paidAt;
    }

    public OffsetDateTime getReversedAt() {
        return reversedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public BigDecimal getTenderedAmount() {
        return tenderedAmount;
    }

    public BigDecimal getChangeAmount() {
        return changeAmount;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public void complete(
        OffsetDateTime at
    ) {

        if (
            status
                != PaymentStatus.PENDING
        ) {
            throw new IllegalStateException(
                "Only a pending payment can be completed."
            );
        }

        status =
            PaymentStatus.COMPLETED;

        paidAt =
            at;
    }
}