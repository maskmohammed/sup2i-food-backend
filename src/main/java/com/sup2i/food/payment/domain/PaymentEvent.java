package com.sup2i.food.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "payment_events")
public class PaymentEvent {

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
        name = "payment_id",
        nullable = false
    )
    private Payment payment;

    @Column(
        name = "event_type",
        nullable = false,
        length = 50
    )
    private String eventType;

    @Column(
        name = "status_before",
        length = 40
    )
    private String statusBefore;

    @Column(
        name = "status_after",
        length = 40
    )
    private String statusAfter;

    @Column(
        name = "external_reference",
        length = 160
    )
    private String externalReference;

    @Column(
        name = "provider_event_id",
        length = 160
    )
    private String providerEventId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
        name = "payload",
        columnDefinition = "jsonb"
    )
    private Map<String, Object> payload;

    @CreationTimestamp
    @Column(
        name = "occurred_at",
        nullable = false,
        updatable = false
    )
    private OffsetDateTime occurredAt;

    protected PaymentEvent() {
    }

    public PaymentEvent(
        Payment payment,
        String eventType,
        PaymentStatus statusBefore,
        PaymentStatus statusAfter,
        String externalReference,
        String providerEventId,
        Map<String, Object> payload
    ) {
        this.payment =
            payment;

        this.eventType =
            eventType;

        this.statusBefore =
            statusBefore == null
                ? null
                : statusBefore.name();

        this.statusAfter =
            statusAfter == null
                ? null
                : statusAfter.name();

        this.externalReference =
            externalReference;

        this.providerEventId =
            providerEventId;

        this.payload =
            payload;
    }

    public UUID getId() {
        return id;
    }

    public Payment getPayment() {
        return payment;
    }

    public String getEventType() {
        return eventType;
    }

    public String getStatusBefore() {
        return statusBefore;
    }

    public String getStatusAfter() {
        return statusAfter;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }
}