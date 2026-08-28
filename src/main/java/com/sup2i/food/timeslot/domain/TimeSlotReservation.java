package com.sup2i.food.timeslot.domain;

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

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "time_slot_reservations")
public class TimeSlotReservation {

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
        name = "time_slot_id",
        nullable = false
    )
    private TimeSlot timeSlot;

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
        name = "status",
        nullable = false,
        length = 30
    )
    private TimeSlotReservationStatus status;

    @CreationTimestamp
    @Column(
        name = "reserved_at",
        nullable = false,
        updatable = false
    )
    private OffsetDateTime reservedAt;

    @Column(name = "released_at")
    private OffsetDateTime releasedAt;

    @Column(
        name = "release_reason",
        columnDefinition = "text"
    )
    private String releaseReason;

    protected TimeSlotReservation() {
    }

    public TimeSlotReservation(
        TimeSlot timeSlot,
        Order order
    ) {
        this.timeSlot =
            timeSlot;

        this.order =
            order;

        this.status =
            TimeSlotReservationStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public TimeSlot getTimeSlot() {
        return timeSlot;
    }

    public Order getOrder() {
        return order;
    }

    public TimeSlotReservationStatus getStatus() {
        return status;
    }

    public OffsetDateTime getReservedAt() {
        return reservedAt;
    }

    public OffsetDateTime getReleasedAt() {
        return releasedAt;
    }

    public String getReleaseReason() {
        return releaseReason;
    }

    public void release(
        OffsetDateTime at,
        String reason
    ) {
        status =
            TimeSlotReservationStatus.RELEASED;

        releasedAt =
            at;

        releaseReason =
            reason;
    }

    public void expire(
        OffsetDateTime at,
        String reason
    ) {
        status =
            TimeSlotReservationStatus.EXPIRED;

        releasedAt =
            at;

        releaseReason =
            reason;
    }
}
