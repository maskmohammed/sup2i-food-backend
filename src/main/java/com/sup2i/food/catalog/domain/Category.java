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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "categories",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_categories_org_slug",
            columnNames = {
                "organization_id",
                "slug"
            }
        )
    }
)
public class Category {

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
        name = "organization_id",
        nullable = false
    )
    private Organization organization;

    @ManyToOne(
        fetch = FetchType.LAZY
    )
    @JoinColumn(
        name = "parent_id"
    )
    private Category parent;

    @Column(
        name = "name",
        nullable = false,
        length = 120
    )
    private String name;

    @Column(
        name = "slug",
        nullable = false,
        length = 140
    )
    private String slug;

    @Column(
        name = "display_order",
        nullable = false
    )
    private int displayOrder;

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

    protected Category() {
    }

    public Category(
        Organization organization,
        Category parent,
        String name,
        String slug,
        int displayOrder,
        boolean active
    ) {
        this.organization = organization;
        this.parent = parent;
        this.name = name;
        this.slug = slug;
        this.displayOrder = displayOrder;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public Category getParent() {
        return parent;
    }

    public void setParent(
        Category parent
    ) {
        this.parent = parent;
    }

    public String getName() {
        return name;
    }

    public void setName(
        String name
    ) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(
        String slug
    ) {
        this.slug = slug;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(
        int displayOrder
    ) {
        this.displayOrder =
            displayOrder;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(
        boolean active
    ) {
        this.active = active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}