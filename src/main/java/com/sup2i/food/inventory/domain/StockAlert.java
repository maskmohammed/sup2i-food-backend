package com.sup2i.food.inventory.domain;

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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_alerts")
public class StockAlert {

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
        name = "stock_item_id",
        nullable = false
    )
    private StockItem stockItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_location_id")
    private StockLocation stockLocation;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "alert_type",
        nullable = false,
        length = 40
    )
    private StockAlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 30
    )
    private StockAlertStatus status;

    @Column(
        name = "threshold_value",
        precision = 14,
        scale = 3
    )
    private BigDecimal thresholdValue;

    @Column(
        name = "observed_value",
        precision = 14,
        scale = 3
    )
    private BigDecimal observedValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id")
    private StockLot lot;

    @Column(
        name = "detected_at",
        nullable = false
    )
    private OffsetDateTime detectedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acknowledged_by")
    private User acknowledgedBy;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "severity",
        nullable = false,
        length = 30
    )
    private StockAlertSeverity severity;

    protected StockAlert() {
    }

    public StockAlert(
        StockItem stockItem,
        StockLocation stockLocation,
        StockAlertType alertType,
        BigDecimal thresholdValue,
        BigDecimal observedValue,
        StockLot lot,
        StockAlertSeverity severity,
        OffsetDateTime detectedAt
    ) {
        this.stockItem =
            stockItem;

        this.stockLocation =
            stockLocation;

        this.alertType =
            alertType;

        this.status =
            StockAlertStatus.OPEN;

        this.thresholdValue =
            thresholdValue;

        this.observedValue =
            observedValue;

        this.lot =
            lot;

        this.detectedAt =
            detectedAt;

        this.severity =
            severity;
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

    public StockAlertType getAlertType() {
        return alertType;
    }

    public StockAlertStatus getStatus() {
        return status;
    }

    public BigDecimal getThresholdValue() {
        return thresholdValue;
    }

    public BigDecimal getObservedValue() {
        return observedValue;
    }

    public StockLot getLot() {
        return lot;
    }

    public OffsetDateTime getDetectedAt() {
        return detectedAt;
    }

    public User getAcknowledgedBy() {
        return acknowledgedBy;
    }

    public OffsetDateTime getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }

    public StockAlertSeverity getSeverity() {
        return severity;
    }

    public void refresh(
        BigDecimal thresholdValue,
        BigDecimal observedValue,
        StockAlertSeverity severity
    ) {
        this.thresholdValue =
            thresholdValue;

        this.observedValue =
            observedValue;

        this.severity =
            severity;
    }

    public void acknowledge(
        User actor,
        OffsetDateTime at
    ) {
        status =
            StockAlertStatus.ACKNOWLEDGED;

        acknowledgedBy =
            actor;

        acknowledgedAt =
            at;
    }

    public void resolve(
        OffsetDateTime at
    ) {
        status =
            StockAlertStatus.RESOLVED;

        resolvedAt =
            at;
    }
}