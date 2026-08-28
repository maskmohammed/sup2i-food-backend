package com.sup2i.food.procurement.domain;

import com.sup2i.food.catalog.domain.Ingredient;
import com.sup2i.food.catalog.domain.Product;
import com.sup2i.food.catalog.domain.ProductVariant;
import com.sup2i.food.common.domain.MeasurementUnit;
import com.sup2i.food.identity.domain.User;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "supplier_contracts")
public class SupplierContract {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id")
    private Ingredient ingredient;

    @Column(
        name = "unit_price",
        nullable = false,
        precision = 12,
        scale = 2
    )
    private BigDecimal unitPrice;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "unit",
        nullable = false,
        length = 20
    )
    private MeasurementUnit unit;

    @Column(
        name = "min_quantity",
        precision = 14,
        scale = 3
    )
    private BigDecimal minQuantity;

    @Column(
        name = "payment_terms",
        length = 60
    )
    private String paymentTerms;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 20
    )
    private SupplierContractStatus status =
        SupplierContractStatus.ACTIVE;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

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

    protected SupplierContract() {
    }

    public SupplierContract(
        Organization organization,
        Supplier supplier,
        Product product,
        ProductVariant variant,
        Ingredient ingredient,
        BigDecimal unitPrice,
        MeasurementUnit unit,
        BigDecimal minQuantity,
        String paymentTerms,
        Integer leadTimeDays,
        LocalDate startDate,
        LocalDate endDate,
        String notes,
        User createdBy
    ) {
        this.organization = organization;

        this.supplier = supplier;

        this.product = product;

        this.variant = variant;

        this.ingredient = ingredient;

        this.unitPrice = unitPrice;

        this.unit = unit;

        this.minQuantity = minQuantity;

        this.paymentTerms = paymentTerms;

        this.leadTimeDays = leadTimeDays;

        this.startDate = startDate;

        this.endDate = endDate;

        this.notes = notes;

        this.createdBy = createdBy;
    }

    public void update(
        BigDecimal unitPrice,
        MeasurementUnit unit,
        BigDecimal minQuantity,
        String paymentTerms,
        Integer leadTimeDays,
        LocalDate startDate,
        LocalDate endDate,
        String notes,
        User updatedBy
    ) {
        this.unitPrice = unitPrice;

        this.unit = unit;

        this.minQuantity = minQuantity;

        this.paymentTerms = paymentTerms;

        this.leadTimeDays = leadTimeDays;

        this.startDate = startDate;

        this.endDate = endDate;

        this.notes = notes;

        this.updatedBy = updatedBy;
    }

    public void setStatus(
        SupplierContractStatus status,
        User updatedBy
    ) {
        this.status = status;

        this.updatedBy = updatedBy;
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

    public Product getProduct() {
        return product;
    }

    public ProductVariant getVariant() {
        return variant;
    }

    public Ingredient getIngredient() {
        return ingredient;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public MeasurementUnit getUnit() {
        return unit;
    }

    public BigDecimal getMinQuantity() {
        return minQuantity;
    }

    public String getPaymentTerms() {
        return paymentTerms;
    }

    public Integer getLeadTimeDays() {
        return leadTimeDays;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public SupplierContractStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public User getUpdatedBy() {
        return updatedBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}