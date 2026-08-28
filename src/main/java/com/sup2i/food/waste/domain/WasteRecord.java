package com.sup2i.food.waste.domain;

import com.sup2i.food.catalog.domain.Ingredient;
import com.sup2i.food.catalog.domain.Product;
import com.sup2i.food.catalog.domain.Recipe;
import com.sup2i.food.common.domain.MeasurementUnit;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.inventory.domain.InventoryMovement;
import com.sup2i.food.inventory.domain.StockLocation;
import com.sup2i.food.order.domain.OrderItem;
import com.sup2i.food.organization.domain.Campus;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "waste_records")
public class WasteRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "organization_id",
        nullable = false
    )
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campus_id")
    private Campus campus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_location_id")
    private StockLocation stockLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id")
    private Recipe recipe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id")
    private Ingredient ingredient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id")
    private OrderItem orderItem;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "waste_type",
        nullable = false,
        length = 30
    )
    private WasteType wasteType;

    @Column(
        name = "quantity",
        nullable = false,
        precision = 14,
        scale = 3
    )
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "unit",
        nullable = false,
        length = 20
    )
    private MeasurementUnit unit;

    @Column(
        name = "estimated_cost",
        nullable = false,
        precision = 12,
        scale = 2
    )
    private BigDecimal estimatedCost;

    @Column(
        name = "reason_text",
        columnDefinition = "TEXT"
    )
    private String reasonText;

    @Column(
        name = "photo_url",
        length = 500
    )
    private String photoUrl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "recorded_by",
        nullable = false
    )
    private User recordedBy;

    @CreationTimestamp
    @Column(
        name = "recorded_at",
        nullable = false,
        updatable = false
    )
    private OffsetDateTime recordedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_movement_id")
    private InventoryMovement inventoryMovement;

    protected WasteRecord() {
    }

    public WasteRecord(
        Organization organization,
        Campus campus,
        StockLocation stockLocation,
        Recipe recipe,
        Ingredient ingredient,
        Product product,
        OrderItem orderItem,
        WasteType wasteType,
        BigDecimal quantity,
        MeasurementUnit unit,
        BigDecimal estimatedCost,
        String reasonText,
        String photoUrl,
        User recordedBy
    ) {
        this.organization = organization;
        this.campus = campus;
        this.stockLocation = stockLocation;
        this.recipe = recipe;
        this.ingredient = ingredient;
        this.product = product;
        this.orderItem = orderItem;
        this.wasteType = wasteType;
        this.quantity = quantity;
        this.unit = unit;
        this.estimatedCost = estimatedCost;
        this.reasonText = reasonText;
        this.photoUrl = photoUrl;
        this.recordedBy = recordedBy;
    }

    public void attachInventoryMovement(
        InventoryMovement inventoryMovement
    ) {
        this.inventoryMovement =
            inventoryMovement;
    }

    public UUID getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public Campus getCampus() {
        return campus;
    }

    public StockLocation getStockLocation() {
        return stockLocation;
    }

    public Recipe getRecipe() {
        return recipe;
    }

    public Ingredient getIngredient() {
        return ingredient;
    }

    public Product getProduct() {
        return product;
    }

    public OrderItem getOrderItem() {
        return orderItem;
    }

    public WasteType getWasteType() {
        return wasteType;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public MeasurementUnit getUnit() {
        return unit;
    }

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }

    public String getReasonText() {
        return reasonText;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public User getRecordedBy() {
        return recordedBy;
    }

    public OffsetDateTime getRecordedAt() {
        return recordedAt;
    }

    public InventoryMovement getInventoryMovement() {
        return inventoryMovement;
    }
}