package com.sup2i.food.production.service;

import com.sup2i.food.common.domain.MeasurementUnit;
import com.sup2i.food.inventory.service.InventoryAlertService;
import com.sup2i.food.production.exception.ProductionConflictException;
import com.sup2i.food.production.exception.ProductionValidationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProductionInventoryConsumptionService {

    private static final String REFERENCE_TYPE =
        "PRODUCTION_RUN_ITEM";

    private static final String MOVEMENT_REASON =
        "Production recipe consumption";

    private static final BigDecimal ZERO =
        BigDecimal.ZERO;

    private final JdbcTemplate jdbcTemplate;

    private final InventoryAlertService
        inventoryAlertService;

    public ProductionInventoryConsumptionService(
        JdbcTemplate jdbcTemplate,
        InventoryAlertService inventoryAlertService
    ) {
        this.jdbcTemplate =
            jdbcTemplate;

        this.inventoryAlertService =
            inventoryAlertService;
    }

    @Transactional(
        propagation = Propagation.MANDATORY
    )
    public void consume(
        UUID actorId,
        UUID organizationId,
        UUID productionRunId,
        UUID kitchenLocationId,
        OffsetDateTime at
    ) {

        requireId(
            actorId,
            "Actor id"
        );

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            productionRunId,
            "Production run id"
        );

        requireId(
            kitchenLocationId,
            "Kitchen location id"
        );

        if (at == null) {
            throw new ProductionValidationException(
                "Production start timestamp is required."
            );
        }

        Integer existingLinks =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM production_run_movements prm
                JOIN production_run_items pri
                  ON pri.id =
                     prm.production_run_item_id
                WHERE pri.production_run_id = ?
                  AND prm.movement_role =
                      'CONSUMPTION'
                """,
                Integer.class,
                productionRunId
            );

        if (
            existingLinks != null
            && existingLinks > 0
        ) {
            throw new ProductionConflictException(
                "A planned production run already has consumption movements."
            );
        }

        List<Requirement> requirements =
            loadRequirements(
                organizationId,
                productionRunId
            );

        if (requirements.isEmpty()) {

            inventoryAlertService
                .reconcileOrganization(
                    organizationId
                );

            return;
        }

        List<MovementPlan> plans =
            new ArrayList<>();

        int index =
            0;

        while (
            index < requirements.size()
        ) {

            UUID stockItemId =
                requirements
                    .get(index)
                    .stockItemId();

            int end =
                index + 1;

            while (
                end < requirements.size()
                && requirements
                    .get(end)
                    .stockItemId()
                    .equals(stockItemId)
            ) {
                end++;
            }

            plans.addAll(
                planStockItem(
                    organizationId,
                    kitchenLocationId,
                    stockItemId,
                    requirements.subList(
                        index,
                        end
                    )
                )
            );

            index =
                end;
        }

        applyPlans(
            actorId,
            productionRunId,
            at,
            plans
        );

        inventoryAlertService
            .reconcileOrganization(
                organizationId
            );
    }

    private List<Requirement> loadRequirements(
        UUID organizationId,
        UUID productionRunId
    ) {

        List<RunItem> runItems =
            jdbcTemplate.query(
                """
                SELECT
                    pri.id,
                    pri.recipe_id,
                    pri.target_quantity,
                    pri.unit
                FROM production_run_items pri
                WHERE pri.production_run_id = ?
                ORDER BY pri.id
                """,
                (resultSet, rowNumber) ->
                    new RunItem(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "recipe_id",
                            UUID.class
                        ),
                        resultSet.getBigDecimal(
                            "target_quantity"
                        ),
                        MeasurementUnit.valueOf(
                            resultSet.getString(
                                "unit"
                            )
                        )
                    ),
                productionRunId
            );

        if (runItems.isEmpty()) {
            throw new ProductionConflictException(
                "Production run has no items."
            );
        }

        List<Requirement> requirements =
            new ArrayList<>();

        for (
            RunItem runItem
            : runItems
        ) {

            if (
                runItem.targetQuantity() == null
                || runItem.targetQuantity()
                    .signum() < 0
            ) {
                throw new ProductionConflictException(
                    "Production target quantity is invalid."
                );
            }

            if (
                runItem.recipeId() == null
                || runItem.targetQuantity()
                    .signum() == 0
            ) {
                continue;
            }

            if (
                runItem.unit()
                    != MeasurementUnit.PIECE
            ) {
                throw new ProductionValidationException(
                    "Recipe production currently requires PIECE output units because no recipe output conversion contract exists."
                );
            }

            List<RecipeLine> lines =
                loadRecipeLines(
                    organizationId,
                    runItem.recipeId()
                );

            if (lines.isEmpty()) {
                throw new ProductionConflictException(
                    "Production recipe is empty."
                );
            }

            for (
                RecipeLine line
                : lines
            ) {

                if (!line.ingredientActive()) {
                    throw new ProductionConflictException(
                        "Production recipe contains an inactive ingredient."
                    );
                }

                if (!line.trackStock()) {
                    continue;
                }

                if (line.stockItemId() == null) {
                    throw new ProductionConflictException(
                        "Tracked recipe ingredient has no stock item."
                    );
                }

                if (
                    line.recipeUnit()
                        != line.ingredientUnit()
                    || line.stockUnit()
                        != line.recipeUnit()
                ) {
                    throw new ProductionValidationException(
                        "Recipe ingredient unit conversion is not configured."
                    );
                }

                BigDecimal wasteFactor =
                    line.wasteFactor() == null
                        ? ZERO
                        : line.wasteFactor();

                BigDecimal required =
                    line.quantity()
                        .multiply(
                            runItem.targetQuantity()
                        )
                        .multiply(
                            BigDecimal.ONE.add(
                                wasteFactor
                            )
                        )
                        .setScale(
                            3,
                            RoundingMode.CEILING
                        );

                if (required.signum() <= 0) {
                    continue;
                }

                requirements.add(
                    new Requirement(
                        runItem.id(),
                        line.stockItemId(),
                        line.recipeUnit(),
                        required
                    )
                );
            }
        }

        requirements.sort(
            Comparator
                .comparing(
                    Requirement::stockItemId
                )
                .thenComparing(
                    Requirement::runItemId
                )
        );

        return List.copyOf(
            requirements
        );
    }

    private List<RecipeLine> loadRecipeLines(
        UUID organizationId,
        UUID recipeId
    ) {

        return jdbcTemplate.query(
            """
            SELECT
                ri.ingredient_id,
                ri.quantity,
                ri.unit AS recipe_unit,
                ri.waste_factor,
                i.base_unit AS ingredient_unit,
                i.is_active AS ingredient_active,
                i.track_stock,
                si.id AS stock_item_id,
                si.base_unit AS stock_unit
            FROM recipe_items ri
            JOIN ingredients i
              ON i.id = ri.ingredient_id
            LEFT JOIN stock_items si
              ON si.organization_id = ?
             AND si.ingredient_id =
                 ri.ingredient_id
            WHERE ri.recipe_id = ?
              AND i.organization_id = ?
            ORDER BY i.id
            """,
            (resultSet, rowNumber) -> {

                String stockUnitText =
                    resultSet.getString(
                        "stock_unit"
                    );

                MeasurementUnit stockUnit =
                    stockUnitText == null
                        ? null
                        : MeasurementUnit.valueOf(
                            stockUnitText
                        );

                return new RecipeLine(
                    resultSet.getObject(
                        "ingredient_id",
                        UUID.class
                    ),
                    resultSet.getBigDecimal(
                        "quantity"
                    ),
                    MeasurementUnit.valueOf(
                        resultSet.getString(
                            "recipe_unit"
                        )
                    ),
                    resultSet.getBigDecimal(
                        "waste_factor"
                    ),
                    MeasurementUnit.valueOf(
                        resultSet.getString(
                            "ingredient_unit"
                        )
                    ),
                    resultSet.getBoolean(
                        "ingredient_active"
                    ),
                    resultSet.getBoolean(
                        "track_stock"
                    ),
                    resultSet.getObject(
                        "stock_item_id",
                        UUID.class
                    ),
                    stockUnit
                );
            },
            organizationId,
            recipeId,
            organizationId
        );
    }

    private List<MovementPlan> planStockItem(
        UUID organizationId,
        UUID kitchenLocationId,
        UUID stockItemId,
        List<Requirement> requirements
    ) {

        List<MutableBalance> balances =
            lockBalances(
                organizationId,
                kitchenLocationId,
                stockItemId
            );

        if (balances.isEmpty()) {
            throw new ProductionConflictException(
                "No ingredient stock balance exists in the production kitchen."
            );
        }

        List<MutableLot> lots =
            lockLots(
                organizationId,
                kitchenLocationId,
                stockItemId
            );

        Map<UUID, List<MutableLot>>
            lotsByLocation =
                new HashMap<>();

        for (
            MutableLot lot
            : lots
        ) {

            lotsByLocation
                .computeIfAbsent(
                    lot.stockLocationId(),
                    ignored ->
                        new ArrayList<>()
                )
                .add(lot);
        }

        List<MovementPlan> plans =
            new ArrayList<>();

        for (
            Requirement requirement
            : requirements
        ) {

            BigDecimal remaining =
                requirement.quantity();

            for (
                MutableBalance balance
                : balances
            ) {

                if (
                    remaining.signum()
                        == 0
                ) {
                    break;
                }

                BigDecimal available =
                    balance.available();

                if (
                    available.signum()
                        <= 0
                ) {
                    continue;
                }

                List<MutableLot> locationLots =
                    lotsByLocation.getOrDefault(
                        balance.stockLocationId(),
                        List.of()
                    );

                BigDecimal lotAvailable =
                    ZERO;

                for (
                    MutableLot lot
                    : locationLots
                ) {
                    lotAvailable =
                        lotAvailable.add(
                            lot.remaining()
                        );
                }

                if (
                    lotAvailable.signum()
                        <= 0
                ) {
                    continue;
                }

                BigDecimal take =
                    remaining
                        .min(available)
                        .min(lotAvailable);

                if (
                    take.signum()
                        <= 0
                ) {
                    continue;
                }

                List<LotPlan> lotPlans =
                    allocateLots(
                        locationLots,
                        take
                    );

                balance.consume(
                    take
                );

                plans.add(
                    new MovementPlan(
                        requirement.runItemId(),
                        requirement.stockItemId(),
                        balance.stockLocationId(),
                        requirement.unit(),
                        take,
                        lotPlans
                    )
                );

                remaining =
                    remaining.subtract(
                        take
                    );
            }

            if (
                remaining.signum()
                    > 0
            ) {
                throw new ProductionConflictException(
                    "Insufficient unreserved ingredient stock for production."
                );
            }
        }

        return plans;
    }

    private List<MutableBalance> lockBalances(
        UUID organizationId,
        UUID kitchenLocationId,
        UUID stockItemId
    ) {

        return jdbcTemplate.query(
            """
            SELECT
                sb.stock_location_id,
                sb.physical_quantity,
                sb.reserved_quantity
            FROM stock_balances sb
            JOIN stock_locations sl
              ON sl.id =
                 sb.stock_location_id
            JOIN locations l
              ON l.id =
                 sl.location_id
            JOIN campuses c
              ON c.id =
                 l.campus_id
            WHERE sb.stock_item_id = ?
              AND sl.location_id = ?
              AND sl.is_active = TRUE
              AND l.is_active = TRUE
              AND c.is_active = TRUE
              AND c.organization_id = ?
            ORDER BY
                sb.stock_location_id
            FOR UPDATE OF sb
            """,
            (resultSet, rowNumber) ->
                new MutableBalance(
                    resultSet.getObject(
                        "stock_location_id",
                        UUID.class
                    ),
                    resultSet.getBigDecimal(
                        "physical_quantity"
                    ),
                    resultSet.getBigDecimal(
                        "reserved_quantity"
                    )
                ),
            stockItemId,
            kitchenLocationId,
            organizationId
        );
    }

    private List<MutableLot> lockLots(
        UUID organizationId,
        UUID kitchenLocationId,
        UUID stockItemId
    ) {

        return jdbcTemplate.query(
            """
            SELECT
                lot.id,
                lot.stock_location_id,
                lot.quantity_remaining
            FROM stock_lots lot
            JOIN stock_locations sl
              ON sl.id =
                 lot.stock_location_id
            JOIN locations l
              ON l.id =
                 sl.location_id
            JOIN campuses c
              ON c.id =
                 l.campus_id
            WHERE lot.stock_item_id = ?
              AND sl.location_id = ?
              AND sl.is_active = TRUE
              AND l.is_active = TRUE
              AND c.is_active = TRUE
              AND c.organization_id = ?
              AND lot.quantity_remaining > 0
            ORDER BY
                lot.stock_location_id,
                CASE
                    WHEN lot.expires_at IS NULL
                    THEN 1
                    ELSE 0
                END,
                lot.expires_at,
                lot.received_at,
                lot.id
            FOR UPDATE OF lot
            """,
            (resultSet, rowNumber) ->
                new MutableLot(
                    resultSet.getObject(
                        "id",
                        UUID.class
                    ),
                    resultSet.getObject(
                        "stock_location_id",
                        UUID.class
                    ),
                    resultSet.getBigDecimal(
                        "quantity_remaining"
                    )
                ),
            stockItemId,
            kitchenLocationId,
            organizationId
        );
    }

    private List<LotPlan> allocateLots(
        List<MutableLot> lots,
        BigDecimal quantity
    ) {

        BigDecimal remaining =
            quantity;

        List<LotPlan> plans =
            new ArrayList<>();

        for (
            MutableLot lot
            : lots
        ) {

            if (
                remaining.signum()
                    == 0
            ) {
                break;
            }

            if (
                lot.remaining()
                    .signum() <= 0
            ) {
                continue;
            }

            BigDecimal take =
                lot.remaining()
                    .min(remaining);

            lot.consume(
                take
            );

            plans.add(
                new LotPlan(
                    lot.id(),
                    take
                )
            );

            remaining =
                remaining.subtract(
                    take
                );
        }

        if (
            remaining.signum()
                > 0
        ) {
            throw new ProductionConflictException(
                "Ingredient lot ledger cannot cover planned production consumption."
            );
        }

        return List.copyOf(
            plans
        );
    }

    private void applyPlans(
        UUID actorId,
        UUID productionRunId,
        OffsetDateTime at,
        List<MovementPlan> plans
    ) {

        for (
            MovementPlan plan
            : plans
        ) {

            int balanceUpdates =
                jdbcTemplate.update(
                    """
                    UPDATE stock_balances
                    SET
                        physical_quantity =
                            physical_quantity - ?,
                        updated_at = ?
                    WHERE stock_item_id = ?
                      AND stock_location_id = ?
                      AND physical_quantity - ?
                          >= reserved_quantity
                    """,
                    plan.quantity(),
                    at,
                    plan.stockItemId(),
                    plan.stockLocationId(),
                    plan.quantity()
                );

            if (balanceUpdates != 1) {
                throw new ProductionConflictException(
                    "Ingredient stock changed concurrently during production."
                );
            }

            for (
                LotPlan lotPlan
                : plan.lots()
            ) {

                int lotUpdates =
                    jdbcTemplate.update(
                        """
                        UPDATE stock_lots
                        SET quantity_remaining =
                            quantity_remaining - ?
                        WHERE id = ?
                          AND quantity_remaining >= ?
                        """,
                        lotPlan.quantity(),
                        lotPlan.stockLotId(),
                        lotPlan.quantity()
                    );

                if (lotUpdates != 1) {
                    throw new ProductionConflictException(
                        "Ingredient stock lot changed concurrently during production."
                    );
                }
            }

            UUID movementId =
                UUID.randomUUID();

            jdbcTemplate.update(
                """
                INSERT INTO inventory_movements (
                    id,
                    stock_item_id,
                    stock_location_id,
                    movement_type,
                    physical_delta,
                    reserved_delta,
                    unit,
                    unit_cost,
                    reference_type,
                    reference_id,
                    reason,
                    comment,
                    performed_by,
                    created_at
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    'RECIPE_CONSUMPTION',
                    ?,
                    0,
                    ?,
                    NULL,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?
                )
                """,
                movementId,
                plan.stockItemId(),
                plan.stockLocationId(),
                plan.quantity()
                    .negate(),
                plan.unit().name(),
                REFERENCE_TYPE,
                plan.runItemId(),
                MOVEMENT_REASON,
                "Production run "
                    + productionRunId,
                actorId,
                at
            );

            for (
                LotPlan lotPlan
                : plan.lots()
            ) {

                jdbcTemplate.update(
                    """
                    INSERT INTO inventory_movement_lots (
                        inventory_movement_id,
                        stock_lot_id,
                        quantity_delta
                    )
                    VALUES (?, ?, ?)
                    """,
                    movementId,
                    lotPlan.stockLotId(),
                    lotPlan.quantity()
                        .negate()
                );
            }

            jdbcTemplate.update(
                """
                INSERT INTO production_run_movements (
                    production_run_item_id,
                    inventory_movement_id,
                    movement_role
                )
                VALUES (
                    ?,
                    ?,
                    'CONSUMPTION'
                )
                """,
                plan.runItemId(),
                movementId
            );
        }
    }

    private void requireId(
        UUID value,
        String label
    ) {

        if (value == null) {
            throw new ProductionValidationException(
                label + " is required."
            );
        }
    }

    private record RunItem(
        UUID id,
        UUID recipeId,
        BigDecimal targetQuantity,
        MeasurementUnit unit
    ) {
    }

    private record RecipeLine(
        UUID ingredientId,
        BigDecimal quantity,
        MeasurementUnit recipeUnit,
        BigDecimal wasteFactor,
        MeasurementUnit ingredientUnit,
        boolean ingredientActive,
        boolean trackStock,
        UUID stockItemId,
        MeasurementUnit stockUnit
    ) {
    }

    private record Requirement(
        UUID runItemId,
        UUID stockItemId,
        MeasurementUnit unit,
        BigDecimal quantity
    ) {
    }

    private record LotPlan(
        UUID stockLotId,
        BigDecimal quantity
    ) {
    }

    private record MovementPlan(
        UUID runItemId,
        UUID stockItemId,
        UUID stockLocationId,
        MeasurementUnit unit,
        BigDecimal quantity,
        List<LotPlan> lots
    ) {
    }

    private static final class MutableBalance {

        private final UUID
            stockLocationId;

        private BigDecimal
            physical;

        private final BigDecimal
            reserved;

        private MutableBalance(
            UUID stockLocationId,
            BigDecimal physical,
            BigDecimal reserved
        ) {

            if (
                stockLocationId == null
                || physical == null
                || reserved == null
                || physical.signum() < 0
                || reserved.signum() < 0
                || reserved.compareTo(
                    physical
                ) > 0
            ) {
                throw new ProductionConflictException(
                    "Ingredient stock balance violates inventory invariants."
                );
            }

            this.stockLocationId =
                stockLocationId;

            this.physical =
                physical;

            this.reserved =
                reserved;
        }

        private UUID stockLocationId() {
            return stockLocationId;
        }

        private BigDecimal available() {
            return physical.subtract(
                reserved
            );
        }

        private void consume(
            BigDecimal quantity
        ) {

            if (
                quantity == null
                || quantity.signum() <= 0
                || available()
                    .compareTo(quantity) < 0
            ) {
                throw new ProductionConflictException(
                    "Production cannot consume reserved or unavailable ingredient stock."
                );
            }

            physical =
                physical.subtract(
                    quantity
                );
        }
    }

    private static final class MutableLot {

        private final UUID id;

        private final UUID
            stockLocationId;

        private BigDecimal
            remaining;

        private MutableLot(
            UUID id,
            UUID stockLocationId,
            BigDecimal remaining
        ) {

            if (
                id == null
                || stockLocationId == null
                || remaining == null
                || remaining.signum() < 0
            ) {
                throw new ProductionConflictException(
                    "Ingredient lot violates inventory invariants."
                );
            }

            this.id =
                id;

            this.stockLocationId =
                stockLocationId;

            this.remaining =
                remaining;
        }

        private UUID id() {
            return id;
        }

        private UUID stockLocationId() {
            return stockLocationId;
        }

        private BigDecimal remaining() {
            return remaining;
        }

        private void consume(
            BigDecimal quantity
        ) {

            if (
                quantity == null
                || quantity.signum() <= 0
                || remaining.compareTo(
                    quantity
                ) < 0
            ) {
                throw new ProductionConflictException(
                    "Ingredient lot cannot satisfy production consumption."
                );
            }

            remaining =
                remaining.subtract(
                    quantity
                );
        }
    }
}