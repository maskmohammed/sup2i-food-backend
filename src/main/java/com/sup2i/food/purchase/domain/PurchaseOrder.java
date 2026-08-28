package com.sup2i.food.purchase.domain;

import com.sup2i.food.identity.domain.User;
import com.sup2i.food.organization.domain.Campus;
import com.sup2i.food.organization.domain.Organization;
import com.sup2i.food.procurement.domain.Supplier;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "organization_id",
        nullable = false
    )
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "supplier_id",
        nullable = false
    )
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "campus_id",
        nullable = false
    )
    private Campus campus;

    @Column(
        name = "reference",
        nullable = false,
        length = 80
    )
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 30
    )
    private PurchaseOrderStatus status =
        PurchaseOrderStatus.DRAFT;

    @Column(
        name = "total_estimated",
        precision = 12,
        scale = 2
    )
    private BigDecimal totalEstimated;

    @Column(
        name = "notes",
        columnDefinition = "TEXT"
    )
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "created_by",
        nullable = false
    )
    private User createdBy;

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

    @OneToMany(
        mappedBy = "purchaseOrder",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    @OrderBy("id asc")
    private List<PurchaseOrderLine> lines =
        new ArrayList<>();

    protected PurchaseOrder() {
    }

    public PurchaseOrder(
        Organization organization,
        Supplier supplier,
        Campus campus,
        String reference,
        String notes,
        User createdBy
    ) {
        this.organization = organization;
        this.supplier = supplier;
        this.campus = campus;
        this.reference = reference;
        this.notes = notes;
        this.createdBy = createdBy;
    }

    public void addLine(
        PurchaseOrderLine line
    ) {
        lines.add(line);
    }

    public void clearLines() {
        lines.clear();
    }

    public void setTotalEstimated(
        BigDecimal totalEstimated
    ) {
        this.totalEstimated =
            totalEstimated;
    }

    public void updateStatus(
        PurchaseOrderStatus status
    ) {
        this.status = status;
    }

    public void updateNotes(
        String notes
    ) {
        this.notes = notes;
    }

    public UUID getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public Campus getCampus() {
        return campus;
    }

    public String getReference() {
        return reference;
    }

    public PurchaseOrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalEstimated() {
        return totalEstimated;
    }

    public String getNotes() {
        return notes;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<PurchaseOrderLine> getLines() {
        return Collections.unmodifiableList(
            lines
        );
    }
}