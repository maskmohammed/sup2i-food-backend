package com.sup2i.food.waste.service;

import com.sup2i.food.catalog.domain.Ingredient;
import com.sup2i.food.catalog.domain.Product;
import com.sup2i.food.catalog.domain.Recipe;
import com.sup2i.food.catalog.repository.IngredientRepository;
import com.sup2i.food.catalog.repository.ProductRepository;
import com.sup2i.food.catalog.repository.RecipeRepository;
import com.sup2i.food.common.domain.MeasurementUnit;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.inventory.domain.InventoryMovement;
import com.sup2i.food.inventory.domain.InventoryMovementType;
import com.sup2i.food.inventory.domain.StockBalance;
import com.sup2i.food.inventory.domain.StockBalanceId;
import com.sup2i.food.inventory.domain.StockItem;
import com.sup2i.food.inventory.domain.StockLocation;
import com.sup2i.food.inventory.repository.InventoryMovementRepository;
import com.sup2i.food.inventory.repository.StockBalanceRepository;
import com.sup2i.food.inventory.repository.StockItemRepository;
import com.sup2i.food.inventory.repository.StockLocationRepository;
import com.sup2i.food.order.domain.OrderItem;
import com.sup2i.food.order.repository.OrderItemRepository;
import com.sup2i.food.organization.domain.Campus;
import com.sup2i.food.organization.repository.CampusRepository;
import com.sup2i.food.waste.api.dto.CreateWasteRecordRequest;
import com.sup2i.food.waste.api.dto.WasteRecordResponse;
import com.sup2i.food.waste.api.dto.WasteStatsResponse;
import com.sup2i.food.waste.api.dto.WasteTypeBreakdown;
import com.sup2i.food.waste.domain.WasteRecord;
import com.sup2i.food.waste.domain.WasteType;
import com.sup2i.food.waste.exception.WasteConflictException;
import com.sup2i.food.waste.exception.WasteNotFoundException;
import com.sup2i.food.waste.exception.WasteValidationException;
import com.sup2i.food.waste.repository.WasteRecordRepository;
import com.sup2i.food.waste.util.WasteCostCalculator;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class WasteService {

    private static final String
        REFERENCE_TYPE =
            "WASTE";

    private final UserRepository userRepository;
    private final CampusRepository campusRepository;
    private final StockLocationRepository stockLocationRepository;
    private final RecipeRepository recipeRepository;
    private final IngredientRepository ingredientRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final StockItemRepository stockItemRepository;
    private final StockBalanceRepository stockBalanceRepository;
    private final InventoryMovementRepository movementRepository;
    private final WasteRecordRepository wasteRecordRepository;

    public WasteService(
        UserRepository userRepository,
        CampusRepository campusRepository,
        StockLocationRepository stockLocationRepository,
        RecipeRepository recipeRepository,
        IngredientRepository ingredientRepository,
        ProductRepository productRepository,
        OrderItemRepository orderItemRepository,
        StockItemRepository stockItemRepository,
        StockBalanceRepository stockBalanceRepository,
        InventoryMovementRepository movementRepository,
        WasteRecordRepository wasteRecordRepository
    ) {
        this.userRepository = userRepository;
        this.campusRepository = campusRepository;
        this.stockLocationRepository = stockLocationRepository;
        this.recipeRepository = recipeRepository;
        this.ingredientRepository = ingredientRepository;
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
        this.stockItemRepository = stockItemRepository;
        this.stockBalanceRepository = stockBalanceRepository;
        this.movementRepository = movementRepository;
        this.wasteRecordRepository = wasteRecordRepository;
    }

    @Transactional
    public WasteRecordResponse record(
        UUID actorId,
        CreateWasteRecordRequest request
    ) {
        User actor = requiredUser(actorId);

        UUID organizationId =
            actor.getOrganization()
                .getId();

        validateTarget(request);

        Campus campus =
            resolveCampus(
                request.campusId(),
                organizationId
            );

        StockLocation stockLocation =
            resolveStockLocation(
                request.stockLocationId(),
                organizationId
            );

        Recipe recipe =
            resolveRecipe(
                request.recipeId(),
                organizationId
            );

        Ingredient ingredient =
            resolveIngredient(
                request.ingredientId(),
                organizationId
            );

        Product product =
            resolveProduct(
                request.productId(),
                organizationId
            );

        OrderItem orderItem =
            resolveOrderItem(
                request.orderItemId(),
                organizationId
            );

        StockItem stockItem =
            resolveStockItem(
                organizationId,
                ingredient,
                product
            );

        BigDecimal unitCost =
            resolveUnitCost(
                stockItem
            );

        BigDecimal estimatedCost =
            WasteCostCalculator.estimate(
                request.quantity(),
                unitCost
            );

        WasteRecord record =
            new WasteRecord(
                actor.getOrganization(),
                campus,
                stockLocation,
                recipe,
                ingredient,
                product,
                orderItem,
                request.wasteType(),
                request.quantity(),
                request.unit(),
                estimatedCost,
                normalizeNullableText(
                    request.reasonText()
                ),
                normalizeNullableText(
                    request.photoUrl()
                ),
                actor
            );

        record =
            wasteRecordRepository
                .save(record);

        if (
            stockItem != null
            && stockLocation != null
        ) {
            InventoryMovement movement =
                applyStockDecrement(
                    actor,
                    stockItem,
                    stockLocation,
                    record,
                    request.quantity(),
                    request.unit(),
                    unitCost
                );

            record.attachInventoryMovement(
                movement
            );

            wasteRecordRepository
                .save(record);
        }

        return WasteRecordResponse.from(record);
    }

    @Transactional(readOnly = true)
    public WasteRecordResponse find(
        UUID actorId,
        UUID recordId
    ) {
        UUID organizationId =
            requiredUser(actorId)
                .getOrganization()
                .getId();

        return WasteRecordResponse.from(
            requiredRecord(
                recordId,
                organizationId
            )
        );
    }

    @Transactional(readOnly = true)
    public List<WasteRecordResponse> findAll(
        UUID actorId,
        WasteType wasteType,
        int limit
    ) {
        UUID organizationId =
            requiredUser(actorId)
                .getOrganization()
                .getId();

        if (wasteType != null) {
            return wasteRecordRepository
                .findAllByOrganization_IdAndWasteTypeOrderByRecordedAtDesc(
                    organizationId,
                    wasteType
                )
                .stream()
                .limit(limit)
                .map(WasteRecordResponse::from)
                .toList();
        }

        return wasteRecordRepository
            .findAllByOrganization_IdOrderByRecordedAtDesc(
                organizationId,
                PageRequest.of(
                    0,
                    limit
                )
            )
            .stream()
            .map(WasteRecordResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public WasteStatsResponse stats(
        UUID actorId
    ) {
        UUID organizationId =
            requiredUser(actorId)
                .getOrganization()
                .getId();

        List<WasteTypeBreakdown> breakdown =
            new ArrayList<>();

        for (
            Object[] row
            : wasteRecordRepository
                .aggregateByType(
                    organizationId
                )
        ) {
            breakdown.add(
                new WasteTypeBreakdown(
                    (WasteType) row[0],
                    (BigDecimal) row[1],
                    (Long) row[2]
                )
            );
        }

        return new WasteStatsResponse(
            wasteRecordRepository
                .countByOrganization(
                    organizationId
                ),
            wasteRecordRepository
                .sumQuantityByOrganization(
                    organizationId
                ),
            wasteRecordRepository
                .sumCostByOrganization(
                    organizationId
                ),
            breakdown
        );
    }

    private WasteRecord requiredRecord(
        UUID recordId,
        UUID organizationId
    ) {
        return wasteRecordRepository
            .findByIdAndOrganization_Id(
                recordId,
                organizationId
            )
            .orElseThrow(() ->
                new WasteNotFoundException(
                    "Waste record does not exist."
                )
            );
    }

    private void validateTarget(
        CreateWasteRecordRequest request
    ) {
        int targets = 0;

        if (request.recipeId() != null) {
            targets++;
        }

        if (request.ingredientId() != null) {
            targets++;
        }

        if (request.orderItemId() != null) {
            targets++;
        }

        if (targets != 1) {
            throw new WasteValidationException(
                "Exactly one of recipe, ingredient or order item must target the waste record."
            );
        }
    }

    private Campus resolveCampus(
        UUID campusId,
        UUID organizationId
    ) {
        if (campusId == null) {
            return null;
        }

        return campusRepository
            .findByIdAndOrganization_Id(
                campusId,
                organizationId
            )
            .orElseThrow(() ->
                new WasteValidationException(
                    "Waste campus does not exist in this organization."
                )
            );
    }

    private StockLocation resolveStockLocation(
        UUID stockLocationId,
        UUID organizationId
    ) {
        if (stockLocationId == null) {
            return null;
        }

        return stockLocationRepository
            .findByIdAndLocation_Campus_Organization_Id(
                stockLocationId,
                organizationId
            )
            .orElseThrow(() ->
                new WasteValidationException(
                    "Waste stock location does not exist in this organization."
                )
            );
    }

    private Recipe resolveRecipe(
        UUID recipeId,
        UUID organizationId
    ) {
        if (recipeId == null) {
            return null;
        }

        return recipeRepository
            .findByIdAndProduct_Organization_Id(
                recipeId,
                organizationId
            )
            .orElseThrow(() ->
                new WasteValidationException(
                    "Waste recipe does not exist in this organization."
                )
            );
    }

    private Ingredient resolveIngredient(
        UUID ingredientId,
        UUID organizationId
    ) {
        if (ingredientId == null) {
            return null;
        }

        return ingredientRepository
            .findByIdAndOrganization_Id(
                ingredientId,
                organizationId
            )
            .orElseThrow(() ->
                new WasteValidationException(
                    "Waste ingredient does not exist in this organization."
                )
            );
    }

    private Product resolveProduct(
        UUID productId,
        UUID organizationId
    ) {
        if (productId == null) {
            return null;
        }

        return productRepository
            .findCatalogProduct(
                productId,
                organizationId
            )
            .orElseThrow(() ->
                new WasteValidationException(
                    "Waste product does not exist in this organization."
                )
            );
    }

    private OrderItem resolveOrderItem(
        UUID orderItemId,
        UUID organizationId
    ) {
        if (orderItemId == null) {
            return null;
        }

        return orderItemRepository
            .findByIdAndOrder_Organization_Id(
                orderItemId,
                organizationId
            )
            .orElseThrow(() ->
                new WasteValidationException(
                    "Waste order item does not exist in this organization."
                )
            );
    }

    private StockItem resolveStockItem(
        UUID organizationId,
        Ingredient ingredient,
        Product product
    ) {
        if (ingredient != null) {
            return stockItemRepository
                .findByOrganization_IdAndIngredient_Id(
                    organizationId,
                    ingredient.getId()
                )
                .orElse(null);
        }

        if (
            product != null
            && !product.isPrepared()
        ) {
            return stockItemRepository
                .findByOrganization_IdAndProduct_Id(
                    organizationId,
                    product.getId()
                )
                .orElse(null);
        }

        return null;
    }

    private BigDecimal resolveUnitCost(
        StockItem stockItem
    ) {
        if (stockItem == null) {
            return null;
        }

        List<BigDecimal> costs =
            movementRepository
                .findRecentUnitCosts(
                    stockItem.getId(),
                    PageRequest.of(
                        0,
                        1
                    )
                );

        return costs.isEmpty()
            ? null
            : costs.get(0);
    }

    private InventoryMovement applyStockDecrement(
        User actor,
        StockItem stockItem,
        StockLocation stockLocation,
        WasteRecord record,
        BigDecimal quantity,
        MeasurementUnit unit,
        BigDecimal unitCost
    ) {
        StockBalanceId balanceId =
            new StockBalanceId(
                stockItem.getId(),
                stockLocation.getId()
            );

        stockBalanceRepository
            .ensureExists(
                stockItem.getId(),
                stockLocation.getId()
            );

        StockBalance balance =
            stockBalanceRepository
                .findLockedById(
                    balanceId
                )
                .orElseThrow(() ->
                    new WasteConflictException(
                        "Stock balance could not be locked."
                    )
                );

        if (
            balance
                .getPhysicalQuantity()
                .compareTo(quantity)
                < 0
        ) {
            throw new WasteConflictException(
                "Insufficient physical stock to record this waste."
            );
        }

        balance.applyPhysicalDelta(
            quantity.negate()
        );

        InventoryMovement movement =
            new InventoryMovement(
                stockItem,
                stockLocation,
                InventoryMovementType.WASTE,
                quantity.negate(),
                BigDecimal.ZERO,
                unit,
                unitCost,
                REFERENCE_TYPE,
                record.getId(),
                "Waste record " + record.getWasteType(),
                record.getReasonText(),
                actor
            );

        movement =
            movementRepository
                .save(movement);

        stockBalanceRepository
            .save(balance);

        return movement;
    }

    private User requiredUser(
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

    private String normalizeNullableText(
        String value
    ) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        return normalized.isEmpty()
            ? null
            : normalized;
    }
}