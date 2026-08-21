package com.sup2i.food.catalog.domain;

import com.sup2i.food.organization.domain.Organization;
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

import java.util.UUID;

@Entity
@Table(
    name = "allergens",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_allergens_org_code",
            columnNames = {
                "organization_id",
                "code"
            }
        )
    }
)
public class Allergen {

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
        length = 120
    )
    private String name;

    @Column(name = "description")
    private String description;

    @Column(
        name = "is_active",
        nullable = false
    )
    private boolean active = true;

    protected Allergen() {
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

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }
}