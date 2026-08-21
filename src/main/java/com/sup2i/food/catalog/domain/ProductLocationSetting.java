package com.sup2i.food.catalog.domain;

import com.sup2i.food.organization.domain.Location;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "product_location_settings",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_product_location",
            columnNames = {
                "product_id",
                "location_id"
            }
        )
    }
)
public class ProductLocationSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled = true;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(
        name = "allowed_days",
        columnDefinition = "smallint[]"
    )
    private Short[] allowedDays;

    @Column(name = "available_from_time")
    private LocalTime availableFromTime;

    @Column(name = "available_to_time")
    private LocalTime availableToTime;

    @Column(name = "preparation_minutes")
    private Integer preparationMinutes;

    @CreationTimestamp
    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ProductLocationSetting() {
    }

    public ProductLocationSetting(
        Product product,
        Location location
    ) {
        this.product = product;
        this.location = location;
    }

    public UUID getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public Location getLocation() {
        return location;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(
        boolean enabled
    ) {
        this.enabled = enabled;
    }

    public Short[] getAllowedDays() {
        return allowedDays;
    }

    public void setAllowedDays(
        Short[] allowedDays
    ) {
        this.allowedDays = allowedDays;
    }

    public LocalTime getAvailableFromTime() {
        return availableFromTime;
    }

    public void setAvailableFromTime(
        LocalTime availableFromTime
    ) {
        this.availableFromTime =
            availableFromTime;
    }

    public LocalTime getAvailableToTime() {
        return availableToTime;
    }

    public void setAvailableToTime(
        LocalTime availableToTime
    ) {
        this.availableToTime =
            availableToTime;
    }

    public Integer getPreparationMinutes() {
        return preparationMinutes;
    }

    public void setPreparationMinutes(
        Integer preparationMinutes
    ) {
        this.preparationMinutes =
            preparationMinutes;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}