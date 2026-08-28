package com.sup2i.food.procurement.domain;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "suppliers")
public class Supplier {

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
        name = "name",
        nullable = false,
        length = 180
    )
    private String name;

    @Column(
        name = "phone",
        length = 40
    )
    private String phone;

    @Column(
        name = "email",
        length = 255
    )
    private String email;

    @Column(
        name = "address",
        columnDefinition = "TEXT"
    )
    private String address;

    @Column(
        name = "contact",
        length = 120
    )
    private String contact;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 20
    )
    private SupplierStatus status =
        SupplierStatus.ACTIVE;

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

    protected Supplier() {
    }

    public Supplier(
        Organization organization,
        String name,
        String phone,
        String email,
        String address,
        String contact,
        SupplierStatus status
    ) {
        this.organization =
            organization;

        this.name = name;

        this.phone = phone;

        this.email = email;

        this.address = address;

        this.contact = contact;

        this.status = status;

        this.active =
            status
                == SupplierStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getAddress() {
        return address;
    }

    public String getContact() {
        return contact;
    }

    public SupplierStatus getStatus() {
        return status;
    }

    public boolean isActive() {
        return active;
    }

    public void update(
        String name,
        String phone,
        String email,
        String address,
        String contact
    ) {
        this.name = name;

        this.phone = phone;

        this.email = email;

        this.address = address;

        this.contact = contact;
    }

    public void setStatus(
        SupplierStatus status
    ) {
        this.status = status;

        this.active =
            status
                == SupplierStatus.ACTIVE;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}