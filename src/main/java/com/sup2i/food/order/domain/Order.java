package com.sup2i.food.order.domain;

import com.sup2i.food.identity.domain.Student;
import com.sup2i.food.organization.domain.Campus;
import com.sup2i.food.organization.domain.Location;
import com.sup2i.food.organization.domain.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "organization_id",
        nullable = false
    )
    private Organization organization;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "campus_id",
        nullable = false
    )
    private Campus campus;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "location_id",
        nullable = false
    )
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;

    @Column(
        name = "order_number",
        nullable = false,
        length = 40
    )
    private String orderNumber;

    @Column(
        name = "business_date",
        nullable = false
    )
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "source",
        nullable = false,
        length = 30
    )
    private OrderSource source;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 40
    )
    private OrderStatus status;

    @Column(name = "slot_id")
    private UUID slotId;

    @Column(
        name = "subtotal",
        nullable = false,
        precision = 12,
        scale = 2
    )
    private BigDecimal subtotal =
        BigDecimal.ZERO;

    @Column(
        name = "discount_total",
        nullable = false,
        precision = 12,
        scale = 2
    )
    private BigDecimal discountTotal =
        BigDecimal.ZERO;

    @Column(
        name = "total",
        nullable = false,
        precision = 12,
        scale = 2
    )
    private BigDecimal total =
        BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(
        name = "currency",
        nullable = false,
        length = 3,
        columnDefinition = "char(3)"
    )
    private String currency =
        "MAD";

    @Column(name = "payment_expires_at")
    private OffsetDateTime paymentExpiresAt;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "ready_at")
    private OffsetDateTime readyAt;

    @Column(name = "collected_at")
    private OffsetDateTime collectedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Version
    @Column(
        name = "version",
        nullable = false
    )
    private int version;

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

    @Enumerated(EnumType.STRING)
    @Column(
        name = "order_type",
        nullable = false,
        length = 40
    )
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "payment_status",
        nullable = false,
        length = 40
    )
    private OrderPaymentStatus paymentStatus;

    @Column(
        name = "tax_total",
        nullable = false,
        precision = 12,
        scale = 2
    )
    private BigDecimal taxTotal =
        BigDecimal.ZERO;

    @Column(name = "promised_at")
    private OffsetDateTime promisedAt;

    @Column(name = "no_show_at")
    private OffsetDateTime noShowAt;

    @Column(
        name = "customer_note",
        columnDefinition = "text"
    )
    private String customerNote;

    protected Order() {
    }

    public Order(
        UUID id,
        Organization organization,
        Campus campus,
        Location location,
        Student student,
        String orderNumber,
        LocalDate businessDate,
        OrderSource source,
        OrderType orderType,
        String currency,
        String customerNote
    ) {
        this.id =
            id;

        this.organization =
            organization;

        this.campus =
            campus;

        this.location =
            location;

        this.student =
            student;

        this.orderNumber =
            orderNumber;

        this.businessDate =
            businessDate;

        this.source =
            source;

        this.orderType =
            orderType;

        this.currency =
            currency;

        this.customerNote =
            customerNote;

        this.status =
            OrderStatus.DRAFT;

        this.paymentStatus =
            OrderPaymentStatus.PENDING;
    }

    public UUID getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public Campus getCampus() {
        return campus;
    }

    public Location getLocation() {
        return location;
    }

    public Student getStudent() {
        return student;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public OrderSource getSource() {
        return source;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public UUID getSlotId() {
        return slotId;
    }

    public void selectSlot(
        UUID slotId
    ) {

        if (
            status
                != OrderStatus.DRAFT
        ) {
            throw new IllegalStateException(
                "Only a DRAFT order can change its slot."
            );
        }

        this.slotId =
            slotId;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDiscountTotal() {
        return discountTotal;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getCurrency() {
        return currency;
    }

    public OffsetDateTime getPaymentExpiresAt() {
        return paymentExpiresAt;
    }

    public OffsetDateTime getPaidAt() {
        return paidAt;
    }

    public OffsetDateTime getReadyAt() {
        return readyAt;
    }

    public OffsetDateTime getCollectedAt() {
        return collectedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public OffsetDateTime getCancelledAt() {
        return cancelledAt;
    }

    public int getVersion() {
        return version;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public OrderPaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public BigDecimal getTaxTotal() {
        return taxTotal;
    }

    public OffsetDateTime getPromisedAt() {
        return promisedAt;
    }

    public OffsetDateTime getNoShowAt() {
        return noShowAt;
    }

    public String getCustomerNote() {
        return customerNote;
    }

    public void updateDraftSnapshot(
        String currency,
        String customerNote,
        BigDecimal subtotal,
        BigDecimal taxTotal,
        BigDecimal discountTotal,
        BigDecimal total
    ) {
        this.currency =
            currency;

        this.customerNote =
            customerNote;

        this.subtotal =
            subtotal;

        this.taxTotal =
            taxTotal;

        this.discountTotal =
            discountTotal;

        this.total =
            total;
    }

    public void markCreated() {
        status =
            OrderStatus.CREATED;
    }

    public void markAwaitingPayment(
        OffsetDateTime paymentExpiresAt
    ) {
        status =
            OrderStatus.AWAITING_PAYMENT;

        paymentStatus =
            OrderPaymentStatus.PENDING;

        this.paymentExpiresAt =
            paymentExpiresAt;
    }

    public void markPaid(
        OffsetDateTime at
    ) {

        if (
            status != OrderStatus.AWAITING_PAYMENT
            || paymentStatus
                != OrderPaymentStatus.PENDING
        ) {
            throw new IllegalStateException(
                "Only an awaiting-payment order can be marked paid."
            );
        }

        status =
            OrderStatus.PAID;

        paymentStatus =
            OrderPaymentStatus.COMPLETED;

        paidAt =
            at;
    }

    public void markQueued() {

        if (
            status
                != OrderStatus.PAID
        ) {
            throw new IllegalStateException(
                "Only a paid order can be queued."
            );
        }

        status =
            OrderStatus.QUEUED;
    }

    public void markPreparing() {

        if (
            status
                != OrderStatus.QUEUED
        ) {
            throw new IllegalStateException(
                "Only a queued order can start preparation."
            );
        }

        status =
            OrderStatus.PREPARING;
    }

    public void markReady(
        OffsetDateTime at
    ) {

        if (
            status
                != OrderStatus.PREPARING
        ) {
            throw new IllegalStateException(
                "Only a preparing order can become ready."
            );
        }

        status =
            OrderStatus.READY;

        readyAt =
            at;
    }

    public void markNoShow(
        OffsetDateTime at
    ) {

        if (
            status
                != OrderStatus.READY
        ) {
            throw new IllegalStateException(
                "Only a READY order can become NO_SHOW."
            );
        }

        if (at == null) {
            throw new IllegalArgumentException(
                "No-show timestamp is required."
            );
        }

        status =
            OrderStatus.NO_SHOW;

        noShowAt =
            at;
    }

    public void markCollected(
        OffsetDateTime at
    ) {

        if (status != OrderStatus.READY) {

            throw new IllegalStateException(
                "Only a READY order can be collected."
            );
        }

        status =
            OrderStatus.COLLECTED;

        collectedAt =
            at;
    }

    public void markCompleted(
        OffsetDateTime at
    ) {

        if (status != OrderStatus.COLLECTED) {

            throw new IllegalStateException(
                "Only a COLLECTED order can be completed."
            );
        }

        status =
            OrderStatus.COMPLETED;

        completedAt =
            at;
    }
    public void markCancelled(
        OffsetDateTime at
    ) {
        status =
            OrderStatus.CANCELLED;

        cancelledAt =
            at;

        if (
            paymentStatus
                == OrderPaymentStatus.PENDING
        ) {
            paymentStatus =
                OrderPaymentStatus.CANCELLED;
        }
    }

    public void markExpired() {
        status =
            OrderStatus.EXPIRED;

        if (
            paymentStatus
                == OrderPaymentStatus.PENDING
        ) {
            paymentStatus =
                OrderPaymentStatus.CANCELLED;
        }
    }
}