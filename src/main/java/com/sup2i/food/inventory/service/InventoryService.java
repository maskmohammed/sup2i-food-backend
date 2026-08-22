package com.sup2i.food.inventory.service;

import com.sup2i.food.catalog.domain.Ingredient;
import com.sup2i.food.catalog.domain.Product;
import com.sup2i.food.catalog.domain.ProductVariant;
import com.sup2i.food.catalog.repository.IngredientRepository;
import com.sup2i.food.catalog.repository.ProductRepository;
import com.sup2i.food.catalog.repository.ProductVariantRepository;
import com.sup2i.food.common.domain.MeasurementUnit;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.inventory.api.dto.ApplyInventoryAdjustmentRequest;
import com.sup2i.food.inventory.api.dto.CreateStockItemRequest;
import com.sup2i.food.inventory.api.dto.CreateStockLocationRequest;
import com.sup2i.food.inventory.api.dto.InventoryAdjustmentResponse;
import com.sup2i.food.inventory.api.dto.InventoryMovementResponse;
import com.sup2i.food.inventory.api.dto.StockBalanceResponse;
import com.sup2i.food.inventory.api.dto.StockItemResponse;
import com.sup2i.food.inventory.api.dto.StockLocationResponse;
import com.sup2i.food.inventory.api.dto.UpdateStockItemRequest;
import com.sup2i.food.inventory.domain.InventoryMovement;
import com.sup2i.food.inventory.domain.InventoryMovementType;
import com.sup2i.food.inventory.domain.StockBalance;
import com.sup2i.food.inventory.domain.StockBalanceId;
import com.sup2i.food.inventory.domain.StockItem;
import com.sup2i.food.inventory.domain.StockLocation;
import com.sup2i.food.inventory.exception.InventoryConflictException;
import com.sup2i.food.inventory.exception.InventoryNotFoundException;
import com.sup2i.food.inventory.exception.InventoryValidationException;
import com.sup2i.food.inventory.repository.InventoryMovementRepository;
import com.sup2i.food.inventory.repository.StockBalanceRepository;
import com.sup2i.food.inventory.repository.StockItemRepository;
import com.sup2i.food.inventory.repository.StockLocationRepository;
import com.sup2i.food.organization.domain.Location;
import com.sup2i.food.organization.repository.LocationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class InventoryService {

    private static final String
        MANUAL_ADJUSTMENT_REFERENCE =
            "MANUAL_ADJUSTMENT";

    private final UserRepository userRepository;
    private final LocationRepository locationRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final IngredientRepository ingredientRepository;
    private final StockLocationRepository stockLocationRepository;
    private final StockItemRepository stockItemRepository;
    private final StockBalanceRepository stockBalanceRepository;
    private final InventoryMovementRepository movementRepository;

    public InventoryService(
        UserRepository userRepository,
        LocationRepository locationRepository,
        ProductRepository productRepository,
        ProductVariantRepository variantRepository,
        IngredientRepository ingredientRepository,
        StockLocationRepository stockLocationRepository,
        StockItemRepository stockItemRepository,
        StockBalanceRepository stockBalanceRepository,
        InventoryMovementRepository movementRepository
    ) {
        this.userRepository = userRepository;
        this.locationRepository = locationRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.ingredientRepository = ingredientRepository;
        this.stockLocationRepository =
            stockLocationRepository;
        this.stockItemRepository =
            stockItemRepository;
        this.stockBalanceRepository =
            stockBalanceRepository;
        this.movementRepository =
            movementRepository;
    }

    @Transactional
    public StockLocationResponse createStockLocation(
        UUID actorId,
        CreateStockLocationRequest request
    ) {

        User actor =
            authenticatedUser(actorId);

        UUID organizationId =
            actor.getOrganization().getId();

        Location location =
            locationRepository
                .findByIdAndCampus_Organization_Id(
                    request.locationId(),
                    organizationId
                )
                .orElseThrow(() ->
                    new InventoryNotFoundException(
                        "Location does not exist."
                    )
                );

        if (!location.isActive()) {
            throw new InventoryValidationException(
                "Location is not active."
            );
        }

        StockLocation stockLocation =
            new StockLocation(
                location,
                request.name().trim(),
                request.type(),
                request.active() == null
                    || request.active()
            );

        try {
            stockLocation =
                stockLocationRepository
                    .saveAndFlush(
                        stockLocation
                    );
        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new InventoryConflictException(
                "Stock location conflicts with an existing resource."
            );
        }

        return stockLocationResponse(
            stockLocation
        );
    }

    @Transactional(readOnly = true)
    public List<StockLocationResponse>
        findStockLocations(
            UUID userId
        ) {

        UUID organizationId =
            authenticatedUser(userId)
                .getOrganization()
                .getId();

        return stockLocationRepository
            .findAllByLocation_Campus_Organization_IdOrderByNameAsc(
                organizationId
            )
            .stream()
            .map(this::stockLocationResponse)
            .toList();
    }

    @Transactional
    public StockItemResponse createStockItem(
        UUID actorId,
        CreateStockItemRequest request
    ) {

        User actor =
            authenticatedUser(actorId);

        UUID organizationId =
            actor.getOrganization().getId();

        Product product = null;
        ProductVariant variant = null;
        Ingredient ingredient = null;

        if (request.productId() != null) {

            product =
                productRepository
                    .findCatalogProduct(
                        request.productId(),
                        organizationId
                    )
                    .orElseThrow(() ->
                        new InventoryNotFoundException(
                            "Product does not exist."
                        )
                    );

            if (!product.isTrackStock()) {
                throw new InventoryValidationException(
                    "Product is not configured to track stock."
                );
            }

            if (
                stockItemRepository
                    .existsByOrganization_IdAndProduct_Id(
                        organizationId,
                        product.getId()
                    )
            ) {
                throw new InventoryConflictException(
                    "A stock item already exists for this product."
                );
            }
        }

        if (request.variantId() != null) {

            variant =
                variantRepository
                    .findByIdAndProduct_Organization_Id(
                        request.variantId(),
                        organizationId
                    )
                    .orElseThrow(() ->
                        new InventoryNotFoundException(
                            "Product variant does not exist."
                        )
                    );

            if (
                !variant.getProduct()
                    .isTrackStock()
            ) {
                throw new InventoryValidationException(
                    "Product is not configured to track stock."
                );
            }

            if (
                stockItemRepository
                    .existsByOrganization_IdAndVariant_Id(
                        organizationId,
                        variant.getId()
                    )
            ) {
                throw new InventoryConflictException(
                    "A stock item already exists for this variant."
                );
            }
        }

        if (request.ingredientId() != null) {

            ingredient =
                ingredientRepository
                    .findByIdAndOrganization_Id(
                        request.ingredientId(),
                        organizationId
                    )
                    .orElseThrow(() ->
                        new InventoryNotFoundException(
                            "Ingredient does not exist."
                        )
                    );

            if (!ingredient.isTrackStock()) {
                throw new InventoryValidationException(
                    "Ingredient is not configured to track stock."
                );
            }

            if (
                request.baseUnit()
                    != ingredient.getBaseUnit()
            ) {
                throw new InventoryValidationException(
                    "Ingredient stock unit must match its base unit."
                );
            }

            if (
                stockItemRepository
                    .existsByOrganization_IdAndIngredient_Id(
                        organizationId,
                        ingredient.getId()
                    )
            ) {
                throw new InventoryConflictException(
                    "A stock item already exists for this ingredient."
                );
            }
        }

        StockItem stockItem =
            new StockItem(
                actor.getOrganization(),
                product,
                variant,
                ingredient,
                request.baseUnit(),
                request.lowStockThreshold(),
                request.trackExpiry() != null
                    && request.trackExpiry()
            );

        try {
            stockItem =
                stockItemRepository
                    .saveAndFlush(
                        stockItem
                    );
        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new InventoryConflictException(
                "Stock item conflicts with an existing resource."
            );
        }

        return stockItemResponse(
            stockItem
        );
    }

    @Transactional
    public StockItemResponse updateStockItem(
        UUID actorId,
        UUID stockItemId,
        UpdateStockItemRequest request
    ) {

        User actor =
            authenticatedUser(actorId);

        StockItem stockItem =
            stockItemForOrganization(
                stockItemId,
                actor.getOrganization()
                    .getId()
            );

        stockItem.updateConfiguration(
            request.lowStockThreshold(),
            request.trackExpiry()
        );

        stockItem =
            stockItemRepository
                .saveAndFlush(stockItem);

        return stockItemResponse(
            stockItem
        );
    }

    @Transactional(readOnly = true)
    public List<StockItemResponse>
        findStockItems(
            UUID userId
        ) {

        UUID organizationId =
            authenticatedUser(userId)
                .getOrganization()
                .getId();

        return stockItemRepository
            .findAllByOrganization_IdOrderByCreatedAtDesc(
                organizationId
            )
            .stream()
            .map(this::stockItemResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<StockBalanceResponse>
        findBalances(
            UUID userId,
            UUID stockLocationId
        ) {

        UUID organizationId =
            authenticatedUser(userId)
                .getOrganization()
                .getId();

        List<StockBalance> balances;

        if (stockLocationId == null) {

            balances =
                stockBalanceRepository
                    .findAllForOrganization(
                        organizationId
                    );
        }
        else {

            stockLocationForOrganization(
                stockLocationId,
                organizationId
            );

            balances =
                stockBalanceRepository
                    .findAllForOrganizationAndLocation(
                        organizationId,
                        stockLocationId
                    );
        }

        return balances.stream()
            .map(this::stockBalanceResponse)
            .toList();
    }

    @Transactional
    public InventoryAdjustmentResponse adjust(
        UUID actorId,
        ApplyInventoryAdjustmentRequest request
    ) {

        User actor =
            authenticatedUser(actorId);

        UUID organizationId =
            actor.getOrganization().getId();

        StockItem stockItem =
            stockItemForOrganization(
                request.stockItemId(),
                organizationId
            );

        StockLocation stockLocation =
            stockLocationForOrganization(
                request.stockLocationId(),
                organizationId
            );

        if (!stockLocation.isActive()) {
            throw new InventoryValidationException(
                "Stock location is not active."
            );
        }

        if (
            request.unit()
                != stockItem.getBaseUnit()
        ) {
            throw new InventoryValidationException(
                "Adjustment unit must match the stock item base unit."
            );
        }

        String reason =
            request.reason().trim();

        String comment =
            normalizeNullableText(
                request.comment()
            );

        stockBalanceRepository.ensureExists(
            stockItem.getId(),
            stockLocation.getId()
        );

        StockBalanceId balanceId =
            new StockBalanceId(
                stockItem.getId(),
                stockLocation.getId()
            );

        StockBalance balance =
            stockBalanceRepository
                .findLockedById(balanceId)
                .orElseThrow(() ->
                    new InventoryConflictException(
                        "Stock balance could not be initialized."
                    )
                );

        InventoryMovement existing =
            movementRepository
                .findByStockItem_IdAndStockLocation_IdAndMovementTypeAndReferenceTypeAndReferenceId(
                    stockItem.getId(),
                    stockLocation.getId(),
                    InventoryMovementType.ADJUSTMENT,
                    MANUAL_ADJUSTMENT_REFERENCE,
                    request.idempotencyKey()
                )
                .orElse(null);

        if (existing != null) {

            if (
                !sameDecimal(
                    existing.getPhysicalDelta(),
                    request.physicalDelta()
                )
                || existing.getUnit()
                    != request.unit()
                || !sameDecimal(
                    existing.getUnitCost(),
                    request.unitCost()
                )
                || !Objects.equals(
                    existing.getReason(),
                    reason
                )
                || !Objects.equals(
                    existing.getComment(),
                    comment
                )
            ) {
                throw new InventoryConflictException(
                    "Idempotency key is already used by another adjustment payload."
                );
            }

            return new InventoryAdjustmentResponse(
                movementResponse(existing),
                stockBalanceResponse(balance),
                true
            );
        }

        BigDecimal newPhysical =
            balance.getPhysicalQuantity()
                .add(
                    request.physicalDelta()
                );

        if (newPhysical.signum() < 0) {
            throw new InventoryConflictException(
                "Adjustment would make physical stock negative."
            );
        }

        if (
            newPhysical.compareTo(
                balance.getReservedQuantity()
            ) < 0
        ) {
            throw new InventoryConflictException(
                "Adjustment would make available stock negative."
            );
        }

        balance.applyPhysicalDelta(
            request.physicalDelta()
        );

        InventoryMovement movement =
            new InventoryMovement(
                stockItem,
                stockLocation,
                InventoryMovementType.ADJUSTMENT,
                request.physicalDelta(),
                BigDecimal.ZERO,
                request.unit(),
                request.unitCost(),
                MANUAL_ADJUSTMENT_REFERENCE,
                request.idempotencyKey(),
                reason,
                comment,
                actor
            );

        try {
            stockBalanceRepository
                .saveAndFlush(balance);

            movement =
                movementRepository
                    .saveAndFlush(movement);
        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new InventoryConflictException(
                "Inventory adjustment violates a stock invariant."
            );
        }

        return new InventoryAdjustmentResponse(
            movementResponse(movement),
            stockBalanceResponse(balance),
            false
        );
    }

    private StockItem stockItemForOrganization(
        UUID stockItemId,
        UUID organizationId
    ) {

        return stockItemRepository
            .findByIdAndOrganization_Id(
                stockItemId,
                organizationId
            )
            .orElseThrow(() ->
                new InventoryNotFoundException(
                    "Stock item does not exist."
                )
            );
    }

    private StockLocation
        stockLocationForOrganization(
            UUID stockLocationId,
            UUID organizationId
        ) {

        return stockLocationRepository
            .findByIdAndLocation_Campus_Organization_Id(
                stockLocationId,
                organizationId
            )
            .orElseThrow(() ->
                new InventoryNotFoundException(
                    "Stock location does not exist."
                )
            );
    }

    private User authenticatedUser(
        UUID userId
    ) {

        return userRepository
            .findById(userId)
            .orElseThrow(() ->
                new BadCredentialsException(
                    "Authenticated user does not exist."
                )
            );
    }

    private StockLocationResponse
        stockLocationResponse(
            StockLocation stockLocation
        ) {

        return new StockLocationResponse(
            stockLocation.getId(),
            stockLocation
                .getLocation()
                .getId(),
            stockLocation.getName(),
            stockLocation.getType(),
            stockLocation.isActive()
        );
    }

    private StockItemResponse stockItemResponse(
        StockItem stockItem
    ) {

        String subjectType;
        UUID productId = null;
        UUID variantId = null;
        UUID ingredientId = null;
        String subjectName;

        if (stockItem.getProduct() != null) {

            subjectType = "PRODUCT";

            productId =
                stockItem.getProduct()
                    .getId();

            subjectName =
                stockItem.getProduct()
                    .getName();
        }
        else if (
            stockItem.getVariant() != null
        ) {

            subjectType = "VARIANT";

            variantId =
                stockItem.getVariant()
                    .getId();

            subjectName =
                stockItem.getVariant()
                    .getProduct()
                    .getName()
                + " - "
                + stockItem.getVariant()
                    .getName();
        }
        else {

            subjectType = "INGREDIENT";

            ingredientId =
                stockItem.getIngredient()
                    .getId();

            subjectName =
                stockItem.getIngredient()
                    .getName();
        }

        return new StockItemResponse(
            stockItem.getId(),
            subjectType,
            productId,
            variantId,
            ingredientId,
            subjectName,
            stockItem.getBaseUnit(),
            stockItem.getLowStockThreshold(),
            stockItem.isTrackExpiry()
        );
    }

    private StockBalanceResponse
        stockBalanceResponse(
            StockBalance balance
        ) {

        return new StockBalanceResponse(
            balance.getStockItem()
                .getId(),
            balance.getStockLocation()
                .getId(),
            balance.getPhysicalQuantity(),
            balance.getReservedQuantity(),
            balance.getAvailableQuantity(),
            balance.getStockItem()
                .getBaseUnit()
        );
    }

    private InventoryMovementResponse
        movementResponse(
            InventoryMovement movement
        ) {

        return new InventoryMovementResponse(
            movement.getId(),
            movement.getStockItem()
                .getId(),
            movement.getStockLocation()
                .getId(),
            movement.getMovementType(),
            movement.getPhysicalDelta(),
            movement.getReservedDelta(),
            movement.getUnit(),
            movement.getUnitCost(),
            movement.getReferenceType(),
            movement.getReferenceId(),
            movement.getReason(),
            movement.getComment(),
            movement.getPerformedBy() == null
                ? null
                : movement
                    .getPerformedBy()
                    .getId(),
            movement.getCreatedAt()
        );
    }

    private boolean sameDecimal(
        BigDecimal left,
        BigDecimal right
    ) {

        if (
            left == null
            || right == null
        ) {
            return left == null
                && right == null;
        }

        return left.compareTo(right) == 0;
    }

    private String normalizeNullableText(
        String value
    ) {

        if (value == null) {
            return null;
        }

        String normalized =
            value.trim();

        return normalized.isEmpty()
            ? null
            : normalized;
    }
}