package com.sup2i.food.inventory.domain;

import com.sup2i.food.identity.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory_sessions")
public class InventorySession {

    @Id
    private UUID id;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "stock_location_id",
        nullable = false
    )
    private StockLocation stockLocation;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 30
    )
    private InventorySessionStatus status;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "started_by",
        nullable = false
    )
    private User startedBy;

    @Column(
        name = "started_at",
        nullable = false
    )
    private OffsetDateTime startedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by")
    private User completedBy;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applied_by")
    private User appliedBy;

    @Column(name = "applied_at")
    private OffsetDateTime appliedAt;

    @Column(
        name = "notes",
        columnDefinition = "TEXT"
    )
    private String notes;

    protected InventorySession() {
    }

    public InventorySession(
        UUID id,
        StockLocation stockLocation,
        User startedBy,
        String notes
    ) {
        this.id = id;
        this.stockLocation =
            stockLocation;
        this.status =
            InventorySessionStatus.OPEN;
        this.startedBy =
            startedBy;
        this.startedAt =
            OffsetDateTime.now();
        this.notes =
            notes;
    }

    public UUID getId() {
        return id;
    }

    public StockLocation getStockLocation() {
        return stockLocation;
    }

    public InventorySessionStatus getStatus() {
        return status;
    }

    public User getStartedBy() {
        return startedBy;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public User getCompletedBy() {
        return completedBy;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public User getAppliedBy() {
        return appliedBy;
    }

    public OffsetDateTime getAppliedAt() {
        return appliedAt;
    }

    public String getNotes() {
        return notes;
    }

    public void markCounting() {

        if (
            status
                == InventorySessionStatus.OPEN
        ) {
            status =
                InventorySessionStatus.COUNTING;
        }
    }

    public void complete(
        User user
    ) {
        status =
            InventorySessionStatus.COMPLETED;

        completedBy =
            user;

        completedAt =
            OffsetDateTime.now();
    }

    public void apply(
        User user
    ) {
        status =
            InventorySessionStatus.APPLIED;

        appliedBy =
            user;

        appliedAt =
            OffsetDateTime.now();
    }

    public void cancel() {
        status =
            InventorySessionStatus.CANCELLED;
    }
}