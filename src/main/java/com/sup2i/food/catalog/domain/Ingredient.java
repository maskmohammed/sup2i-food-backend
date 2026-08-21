package com.sup2i.food.catalog.domain;

import com.sup2i.food.common.domain.MeasurementUnit;
import com.sup2i.food.organization.domain.Organization;
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
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "ingredients",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_ingredients_org_code",
            columnNames = {
                "organization_id",
                "code"
            }
        )
    }
)
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "organization_id",
        nullable = false
    )
    private Organization organization;

    @Column(
        name = "code",
        nullable = false,
        length = 80
    )
    private String code;

    @Column(
        name = "name",
        nullable = false,
        length = 150
    )
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "base_unit",
        nullable = false,
        length = 20
    )
    private MeasurementUnit baseUnit;

    @Column(
        name = "is_active",
        nullable = false
    )
    private boolean active = true;

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

    protected Ingredient() {
    }

    public Ingredient(
        Organization organization,
        String code,
        String name,
        MeasurementUnit baseUnit,
        boolean active
    ) {
        this.organization = organization;
        this.code = code;
        this.name = name;
        this.baseUnit = baseUnit;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public MeasurementUnit getBaseUnit() {
        return baseUnit;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}