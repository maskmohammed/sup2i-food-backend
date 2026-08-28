package com.sup2i.food.timeslot.domain;

import com.sup2i.food.organization.domain.Location;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "time_slots")
public class TimeSlot {

    @Id
    private UUID id;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "location_id",
        nullable = false
    )
    private Location location;

    @Column(
        name = "slot_date",
        nullable = false
    )
    private LocalDate slotDate;

    @Column(
        name = "start_time",
        nullable = false
    )
    private LocalTime startTime;

    @Column(
        name = "end_time",
        nullable = false
    )
    private LocalTime endTime;

    @Column(
        name = "capacity",
        nullable = false
    )
    private int capacity;

    @Column(
        name = "reserved_count",
        nullable = false
    )
    private int reservedCount;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 30
    )
    private TimeSlotStatus status;

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

    protected TimeSlot() {
    }

    public UUID getId() {
        return id;
    }

    public Location getLocation() {
        return location;
    }

    public LocalDate getSlotDate() {
        return slotDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getReservedCount() {
        return reservedCount;
    }

    public int getRemainingCapacity() {
        return capacity - reservedCount;
    }

    public TimeSlotStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isPast(
        LocalDateTime now
    ) {
        return LocalDateTime.of(
                slotDate,
                startTime
            )
            .isBefore(now);
    }

    public boolean isClosed() {
        return status
            == TimeSlotStatus.CLOSED;
    }

    public boolean hasCapacity() {
        return reservedCount
            < capacity;
    }

    public void reserve() {

        if (!hasCapacity()) {
            throw new IllegalStateException(
                "Time slot is full."
            );
        }

        reservedCount++;

        if (reservedCount == capacity) {
            status =
                TimeSlotStatus.FULL;
        }
    }

    public void release() {

        if (reservedCount <= 0) {
            throw new IllegalStateException(
                "Reserved count cannot become negative."
            );
        }

        reservedCount--;

        if (
            status
                == TimeSlotStatus.FULL
        ) {
            status =
                TimeSlotStatus.OPEN;
        }
    }
}
