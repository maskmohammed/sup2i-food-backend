package com.sup2i.food.waste.service;

import com.sup2i.food.common.domain.MeasurementUnit;
import com.sup2i.food.inventory.service.InventoryAlertService;
import com.sup2i.food.waste.api.dto.RecordWasteCommand;
import com.sup2i.food.waste.api.dto.WasteRecordResponse;
import com.sup2i.food.waste.domain.WasteCategory;
import com.sup2i.food.waste.exception.WasteConflictException;
import com.sup2i.food.waste.exception.WasteNotFoundException;
import com.sup2i.food.waste.exception.WasteValidationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class WasteService {

    private static final BigDecimal ZERO =
        new BigDecimal("0.000");

    private final JdbcTemplate jdbcTemplate;

    private final InventoryAlertService
        inventoryAlertService;

    public WasteService(
        JdbcTemplate jdbcTemplate,
        InventoryAlertService inventoryAlertService
    ) {
        this.jdbcTemplate =
            jdbcTemplate;

        this.inventoryAlertService =
            inventoryAlertService;
    }

    @Transactional
    public WasteRecordResponse record(
        UUID actorId,
        UUID wasteRecordId,
        RecordWasteCommand command
    ) {

        UUID organizationId =
            organizationId(actorId);

        requireId(
            wasteRecordId,
            "Waste record id"
        );

        validateCommand(
            command
        );

        lockMutationKey(
            wasteRecordId
        );

        BigDecimal quantity =
            quantity(
                command.quantity()
            );

        BigDecimal estimatedCost =
            money(
                command.estimatedCost(),
                "Estimated waste cost"
            );

        String notes =
            nullableText(
                command.notes()
            );

        StockContext stock =
            stock(
                organizationId,
                command.stockItemId()
            );

        UUID resolvedProductId =
            resolveProduct(
                organizationId,
                stock,
                command.productId()
            );

        WasteRecordResponse existing =
            findOwnedRecord(
                organizationId,
                wasteRecordId,
                false
            );

        if (existing != null) {

            if (
                samePayload(
                    existing,
                    command,
                    quantity,
                    estimatedCost,
                    notes,
                    resolvedProductId
                )
            ) {
                return replay(existing);
            }

            throw new WasteConflictException(
                "Waste record identifier is already used by another payload."
            );
        }

        ReasonContext reason =
            activeReason(
                organizationId,
                command.wasteReasonId()
            );

        if (
            reason.requiresComment()
            && notes == null
        ) {
            throw new WasteValidationException(
                "Selected waste reason requires a comment."
            );
        }

        if (
            stock.unit()
                != command.unit()
        ) {
            throw new WasteValidationException(
                "Waste unit must match stock item base unit because no waste unit-conversion contract exists."
            );
        }

        ownedStockLocation(
            organizationId,
            command.stockLocationId()
        );

        if (command.orderItemId() != null) {

            OrderItemContext orderItem =
                orderItem(
                    organizationId,
                    command.orderItemId()
                );

            requireStockSubjectMatch(
                stock,
                orderItem.productId(),
                orderItem.variantId(),
                "Order item"
            );

            if (resolvedProductId == null) {
                resolvedProductId =
                    orderItem.productId();
            }

            if (
                !resolvedProductId.equals(
                    orderItem.productId()
                )
            ) {
                throw new WasteConflictException(
                    "Waste product does not match order item product."
                );
            }
        }

        ProductionContext production =
            null;

        if (
            command.productionRunItemId()
                != null
        ) {

            production =
                lockedProductionItem(
                    organizationId,
                    command.productionRunItemId()
                );

            requireStockSubjectMatch(
                stock,
                production.productId(),
                production.variantId(),
                "Production run item"
            );

            if (
                production.unit()
                    != command.unit()
            ) {
                throw new WasteValidationException(
                    "Waste unit must match production run item unit."
                );
            }

            if (resolvedProductId == null) {
                resolvedProductId =
                    production.productId();
            }

            if (
                !resolvedProductId.equals(
                    production.productId()
                )
            ) {
                throw new WasteConflictException(
                    "Waste product does not match production run item product."
                );
            }

            validateProductionCapacity(
                production,
                quantity
            );
        }

        BalanceContext balance =
            lockedBalance(
                organizationId,
                command.stockItemId(),
                command.stockLocationId()
            );

        BigDecimal available =
            balance.physicalQuantity()
                .subtract(
                    balance.reservedQuantity()
                );

        if (
            available.compareTo(
                quantity
            ) < 0
        ) {
            throw new WasteConflictException(
                "Not enough unreserved physical stock to record this waste."
            );
        }

        List<LotContext> lots =
            lockedLots(
                command.stockItemId(),
                command.stockLocationId()
            );

        List<LotAllocation> allocations =
            planLotConsumption(
                lots,
                quantity
            );

        UUID movementId =
            UUID.randomUUID();

        BigDecimal unitCost =
            estimatedCost == null
                ? null
                : estimatedCost
                    .divide(
                        quantity,
                        2,
                        RoundingMode.HALF_UP
                    );

        try {

            int balanceUpdated =
                jdbcTemplate.update(
                    """
                    UPDATE stock_balances
                    SET
                        physical_quantity =
                            physical_quantity - ?,
                        updated_at =
                            CURRENT_TIMESTAMP
                    WHERE stock_item_id = ?
                      AND stock_location_id = ?
                      AND physical_quantity - ?
                          >= reserved_quantity
                    """,
                    quantity,
                    command.stockItemId(),
                    command.stockLocationId(),
                    quantity
                );

            if (balanceUpdated != 1) {
                throw new WasteConflictException(
                    "Stock balance changed concurrently while recording waste."
                );
            }

            jdbcTemplate.update(
                """
                INSERT INTO inventory_movements(
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
                    performed_by
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    'WASTE',
                    ?,
                    0,
                    ?,
                    ?,
                    'WASTE_RECORD',
                    ?,
                    ?,
                    ?,
                    ?
                )
                """,
                movementId,
                command.stockItemId(),
                command.stockLocationId(),
                quantity.negate(),
                command.unit().name(),
                unitCost,
                wasteRecordId,
                reason.code(),
                notes,
                actorId
            );

            for (
                LotAllocation allocation
                : allocations
            ) {

                int lotUpdated =
                    jdbcTemplate.update(
                        """
                        UPDATE stock_lots
                        SET quantity_remaining =
                            quantity_remaining - ?
                        WHERE id = ?
                          AND quantity_remaining >= ?
                        """,
                        allocation.quantity(),
                        allocation.lotId(),
                        allocation.quantity()
                    );

                if (lotUpdated != 1) {
                    throw new WasteConflictException(
                        "Stock lot changed concurrently while recording waste."
                    );
                }

                jdbcTemplate.update(
                    """
                    INSERT INTO inventory_movement_lots(
                        inventory_movement_id,
                        stock_lot_id,
                        quantity_delta
                    )
                    VALUES (?, ?, ?)
                    """,
                    movementId,
                    allocation.lotId(),
                    allocation.quantity()
                        .negate()
                );
            }

            jdbcTemplate.update(
                """
                INSERT INTO waste_records(
                    id,
                    stock_item_id,
                    stock_location_id,
                    quantity,
                    unit,
                    reason,
                    estimated_cost,
                    notes,
                    recorded_by,
                    inventory_movement_id,
                    waste_reason_id,
                    product_id,
                    order_item_id,
                    production_run_item_id
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?
                )
                """,
                wasteRecordId,
                command.stockItemId(),
                command.stockLocationId(),
                quantity,
                command.unit().name(),
                reason.category().name(),
                estimatedCost,
                notes,
                actorId,
                movementId,
                reason.id(),
                resolvedProductId,
                command.orderItemId(),
                command.productionRunItemId()
            );

            if (
                command.productionRunItemId()
                    != null
            ) {

                jdbcTemplate.update(
                    """
                    INSERT INTO production_run_movements(
                        production_run_item_id,
                        inventory_movement_id,
                        movement_role
                    )
                    VALUES (?, ?, 'WASTE')
                    """,
                    command.productionRunItemId(),
                    movementId
                );
            }

        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new WasteConflictException(
                "Waste mutation violates a stock or traceability invariant."
            );
        }

        inventoryAlertService
            .reconcileOrganization(
                organizationId
            );

        WasteRecordResponse created =
            findOwnedRecord(
                organizationId,
                wasteRecordId,
                false
            );

        if (created == null) {
            throw new WasteConflictException(
                "Waste record was not persisted."
            );
        }

        return created;
    }

    @Transactional(readOnly = true)
    public WasteRecordResponse get(
        UUID actorId,
        UUID wasteRecordId
    ) {

        UUID organizationId =
            organizationId(actorId);

        requireId(
            wasteRecordId,
            "Waste record id"
        );

        WasteRecordResponse response =
            findOwnedRecord(
                organizationId,
                wasteRecordId,
                false
            );

        if (response == null) {
            throw new WasteNotFoundException(
                "Waste record does not exist."
            );
        }

        return response;
    }

    @Transactional(readOnly = true)
    public List<WasteRecordResponse> list(
        UUID actorId,
        UUID stockLocationId,
        UUID wasteReasonId,
        UUID productionRunItemId,
        int limit
    ) {

        UUID organizationId =
            organizationId(actorId);

        if (
            limit < 1
            || limit > 200
        ) {
            throw new WasteValidationException(
                "Waste list limit must be between 1 and 200."
            );
        }

        StringBuilder sql =
            new StringBuilder(
                """
                SELECT
                    wr.id,
                    wr.stock_item_id,
                    wr.stock_location_id,
                    wr.quantity,
                    wr.unit,
                    wr.waste_reason_id,
                    reason.code AS waste_reason_code,
                    reason.category AS waste_category,
                    wr.estimated_cost,
                    wr.notes,
                    wr.product_id,
                    wr.order_item_id,
                    wr.production_run_item_id,
                    wr.inventory_movement_id,
                    wr.recorded_by,
                    wr.recorded_at
                FROM waste_records wr
                JOIN waste_reasons reason
                  ON reason.id = wr.waste_reason_id
                JOIN stock_items item
                  ON item.id = wr.stock_item_id
                JOIN stock_locations stock_location
                  ON stock_location.id = wr.stock_location_id
                JOIN locations location
                  ON location.id = stock_location.location_id
                JOIN campuses campus
                  ON campus.id = location.campus_id
                WHERE item.organization_id = ?
                  AND reason.organization_id = ?
                  AND campus.organization_id = ?
                """
            );

        List<Object> arguments =
            new ArrayList<>();

        arguments.add(
            organizationId
        );

        arguments.add(
            organizationId
        );

        arguments.add(
            organizationId
        );

        if (stockLocationId != null) {

            sql.append(
                " AND wr.stock_location_id = ?"
            );

            arguments.add(
                stockLocationId
            );
        }

        if (wasteReasonId != null) {

            sql.append(
                " AND wr.waste_reason_id = ?"
            );

            arguments.add(
                wasteReasonId
            );
        }

        if (productionRunItemId != null) {

            sql.append(
                " AND wr.production_run_item_id = ?"
            );

            arguments.add(
                productionRunItemId
            );
        }

        sql.append(
            " ORDER BY wr.recorded_at DESC, wr.id DESC LIMIT ?"
        );

        arguments.add(
            limit
        );

        return jdbcTemplate.query(
            sql.toString(),
            (resultSet, rowNumber) ->
                response(
                    resultSet,
                    false
                ),
            arguments.toArray()
        );
    }

    private void lockMutationKey(
        UUID wasteRecordId
    ) {

        long lockKey =
            wasteRecordId
                .getMostSignificantBits()
                ^ wasteRecordId
                    .getLeastSignificantBits();

        jdbcTemplate.execute(
            (ConnectionCallback<Void>) connection -> {

                try (
                    PreparedStatement statement =
                        connection.prepareStatement(
                            "SELECT pg_advisory_xact_lock(?)"
                        )
                ) {

                    statement.setLong(
                        1,
                        lockKey
                    );

                    statement.execute();
                }

                return null;
            }
        );
    }

    private ReasonContext activeReason(
        UUID organizationId,
        UUID reasonId
    ) {

        requireId(
            reasonId,
            "Waste reason id"
        );

        List<ReasonContext> rows =
            jdbcTemplate.query(
                """
                SELECT
                    id,
                    code,
                    category,
                    requires_comment
                FROM waste_reasons
                WHERE id = ?
                  AND organization_id = ?
                  AND is_active = TRUE
                """,
                (resultSet, rowNumber) ->
                    new ReasonContext(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getString(
                            "code"
                        ),
                        WasteCategory.valueOf(
                            resultSet.getString(
                                "category"
                            )
                        ),
                        resultSet.getBoolean(
                            "requires_comment"
                        )
                    ),
                reasonId,
                organizationId
            );

        if (rows.size() != 1) {
            throw new WasteNotFoundException(
                "Active waste reason does not exist."
            );
        }

        return rows.get(0);
    }

    private StockContext stock(
        UUID organizationId,
        UUID stockItemId
    ) {

        requireId(
            stockItemId,
            "Stock item id"
        );

        List<StockContext> rows =
            jdbcTemplate.query(
                """
                SELECT
                    si.id,
                    COALESCE(
                        si.product_id,
                        pv.product_id
                    ) AS effective_product_id,
                    si.variant_id,
                    si.ingredient_id,
                    si.base_unit
                FROM stock_items si
                LEFT JOIN product_variants pv
                  ON pv.id = si.variant_id
                WHERE si.id = ?
                  AND si.organization_id = ?
                """,
                (resultSet, rowNumber) ->
                    new StockContext(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "effective_product_id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "variant_id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "ingredient_id",
                            UUID.class
                        ),
                        MeasurementUnit.valueOf(
                            resultSet.getString(
                                "base_unit"
                            )
                        )
                    ),
                stockItemId,
                organizationId
            );

        if (rows.size() != 1) {
            throw new WasteNotFoundException(
                "Stock item does not exist."
            );
        }

        return rows.get(0);
    }

    private void ownedStockLocation(
        UUID organizationId,
        UUID stockLocationId
    ) {

        requireId(
            stockLocationId,
            "Stock location id"
        );

        Integer count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM stock_locations sl
                JOIN locations l
                  ON l.id = sl.location_id
                JOIN campuses c
                  ON c.id = l.campus_id
                WHERE sl.id = ?
                  AND sl.is_active = TRUE
                  AND l.is_active = TRUE
                  AND c.is_active = TRUE
                  AND c.organization_id = ?
                """,
                Integer.class,
                stockLocationId,
                organizationId
            );

        if (
            count == null
            || count != 1
        ) {
            throw new WasteNotFoundException(
                "Active stock location does not exist in actor organization."
            );
        }
    }

    private UUID resolveProduct(
        UUID organizationId,
        StockContext stock,
        UUID requestedProductId
    ) {

        UUID resolved =
            requestedProductId;

        if (requestedProductId != null) {

            Integer count =
                jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM products
                    WHERE id = ?
                      AND organization_id = ?
                    """,
                    Integer.class,
                    requestedProductId,
                    organizationId
                );

            if (
                count == null
                || count != 1
            ) {
                throw new WasteNotFoundException(
                    "Product does not exist in actor organization."
                );
            }
        }

        if (stock.effectiveProductId() != null) {

            if (resolved == null) {
                resolved =
                    stock.effectiveProductId();
            }

            if (
                !resolved.equals(
                    stock.effectiveProductId()
                )
            ) {
                throw new WasteConflictException(
                    "Waste product does not match stock item product."
                );
            }
        }

        return resolved;
    }

    private OrderItemContext orderItem(
        UUID organizationId,
        UUID orderItemId
    ) {

        List<OrderItemContext> rows =
            jdbcTemplate.query(
                """
                SELECT
                    oi.product_id,
                    oi.variant_id
                FROM order_items oi
                JOIN orders o
                  ON o.id = oi.order_id
                JOIN locations l
                  ON l.id = o.location_id
                JOIN campuses c
                  ON c.id = l.campus_id
                WHERE oi.id = ?
                  AND c.organization_id = ?
                """,
                (resultSet, rowNumber) ->
                    new OrderItemContext(
                        resultSet.getObject(
                            "product_id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "variant_id",
                            UUID.class
                        )
                    ),
                orderItemId,
                organizationId
            );

        if (rows.size() != 1) {
            throw new WasteNotFoundException(
                "Order item does not exist in actor organization."
            );
        }

        return rows.get(0);
    }

    private ProductionContext lockedProductionItem(
        UUID organizationId,
        UUID productionRunItemId
    ) {

        List<ProductionContext> rows =
            jdbcTemplate.query(
                """
                SELECT
                    pri.id,
                    pri.product_id,
                    pri.variant_id,
                    pri.prepared_quantity,
                    pri.unit,
                    pr.status
                FROM production_run_items pri
                JOIN production_runs pr
                  ON pr.id = pri.production_run_id
                WHERE pri.id = ?
                  AND pr.organization_id = ?
                FOR UPDATE OF pri
                """,
                (resultSet, rowNumber) ->
                    new ProductionContext(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "product_id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "variant_id",
                            UUID.class
                        ),
                        resultSet.getBigDecimal(
                            "prepared_quantity"
                        ),
                        MeasurementUnit.valueOf(
                            resultSet.getString(
                                "unit"
                            )
                        ),
                        resultSet.getString(
                            "status"
                        )
                    ),
                productionRunItemId,
                organizationId
            );

        if (rows.size() != 1) {
            throw new WasteNotFoundException(
                "Production run item does not exist in actor organization."
            );
        }

        ProductionContext context =
            rows.get(0);

        boolean started =
            "IN_PROGRESS".equals(
                context.status()
            )
                || "COMPLETED".equals(
                    context.status()
                );

        if (!started) {
            throw new WasteConflictException(
                "Only started production can be linked to waste."
            );
        }

        return context;
    }

    private void validateProductionCapacity(
        ProductionContext production,
        BigDecimal newWaste
    ) {

        BigDecimal alreadyWasted =
            jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(
                    SUM(quantity),
                    0
                )
                FROM waste_records
                WHERE production_run_item_id = ?
                """,
                BigDecimal.class,
                production.id()
            );

        BigDecimal allocated =
            jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(
                    SUM(quantity),
                    0
                )
                FROM production_allocations
                WHERE production_run_item_id = ?
                """,
                BigDecimal.class,
                production.id()
            );

        if (alreadyWasted == null) {
            alreadyWasted =
                ZERO;
        }

        if (allocated == null) {
            allocated =
                ZERO;
        }

        BigDecimal accounted =
            alreadyWasted
                .add(
                    allocated
                )
                .add(
                    newWaste
                );

        if (
            accounted.compareTo(
                production.preparedQuantity()
            ) > 0
        ) {
            throw new WasteConflictException(
                "Allocated plus wasted production cannot exceed actual prepared quantity."
            );
        }
    }

    private void requireStockSubjectMatch(
        StockContext stock,
        UUID expectedProductId,
        UUID expectedVariantId,
        String source
    ) {

        if (stock.effectiveProductId() == null) {
            throw new WasteConflictException(
                source
                    + " linkage requires product or variant stock; ingredient stock was already consumed by its own stock workflow."
            );
        }

        if (
            !stock.effectiveProductId()
                .equals(
                    expectedProductId
                )
        ) {
            throw new WasteConflictException(
                source
                    + " product does not match stock item."
            );
        }

        if (
            !Objects.equals(
                stock.variantId(),
                expectedVariantId
            )
        ) {
            throw new WasteConflictException(
                source
                    + " variant does not match stock item variant."
            );
        }
    }

    private BalanceContext lockedBalance(
        UUID organizationId,
        UUID stockItemId,
        UUID stockLocationId
    ) {

        List<BalanceContext> rows =
            jdbcTemplate.query(
                """
                SELECT
                    sb.physical_quantity,
                    sb.reserved_quantity
                FROM stock_balances sb
                JOIN stock_items si
                  ON si.id = sb.stock_item_id
                JOIN stock_locations sl
                  ON sl.id = sb.stock_location_id
                JOIN locations l
                  ON l.id = sl.location_id
                JOIN campuses c
                  ON c.id = l.campus_id
                WHERE sb.stock_item_id = ?
                  AND sb.stock_location_id = ?
                  AND si.organization_id = ?
                  AND c.organization_id = ?
                FOR UPDATE OF sb
                """,
                (resultSet, rowNumber) ->
                    new BalanceContext(
                        resultSet.getBigDecimal(
                            "physical_quantity"
                        ),
                        resultSet.getBigDecimal(
                            "reserved_quantity"
                        )
                    ),
                stockItemId,
                stockLocationId,
                organizationId,
                organizationId
            );

        if (rows.size() != 1) {
            throw new WasteNotFoundException(
                "Stock balance does not exist."
            );
        }

        return rows.get(0);
    }

    private List<LotContext> lockedLots(
        UUID stockItemId,
        UUID stockLocationId
    ) {

        return jdbcTemplate.query(
            """
            SELECT
                lot.id,
                lot.quantity_remaining
            FROM stock_lots lot
            WHERE lot.stock_item_id = ?
              AND lot.stock_location_id = ?
              AND lot.quantity_remaining > 0
            ORDER BY
                CASE
                    WHEN lot.expires_at IS NULL
                    THEN 1
                    ELSE 0
                END ASC,
                lot.expires_at ASC,
                lot.received_at ASC,
                lot.id ASC
            FOR UPDATE OF lot
            """,
            (resultSet, rowNumber) ->
                new LotContext(
                    resultSet.getObject(
                        "id",
                        UUID.class
                    ),
                    resultSet.getBigDecimal(
                        "quantity_remaining"
                    )
                ),
            stockItemId,
            stockLocationId
        );
    }

    private List<LotAllocation> planLotConsumption(
        List<LotContext> lots,
        BigDecimal requested
    ) {

        BigDecimal remaining =
            requested;

        List<LotAllocation> allocations =
            new ArrayList<>();

        for (
            LotContext lot
            : lots
        ) {

            if (remaining.signum() <= 0) {
                break;
            }

            BigDecimal take =
                lot.quantityRemaining()
                    .min(
                        remaining
                    );

            if (take.signum() > 0) {

                allocations.add(
                    new LotAllocation(
                        lot.id(),
                        take
                    )
                );

                remaining =
                    remaining.subtract(
                        take
                    );
            }
        }

        /*
         * A positive stock balance can legitimately contain quantity
         * introduced by an adjustment without a stock_lot.
         * Therefore lot rows are consumed FEFO where available,
         * while stock balance remains the authoritative physical cache.
         */

        return List.copyOf(
            allocations
        );
    }

    private WasteRecordResponse findOwnedRecord(
        UUID organizationId,
        UUID wasteRecordId,
        boolean replayed
    ) {

        List<WasteRecordResponse> rows =
            jdbcTemplate.query(
                """
                SELECT
                    wr.id,
                    wr.stock_item_id,
                    wr.stock_location_id,
                    wr.quantity,
                    wr.unit,
                    wr.waste_reason_id,
                    reason.code AS waste_reason_code,
                    reason.category AS waste_category,
                    wr.estimated_cost,
                    wr.notes,
                    wr.product_id,
                    wr.order_item_id,
                    wr.production_run_item_id,
                    wr.inventory_movement_id,
                    wr.recorded_by,
                    wr.recorded_at
                FROM waste_records wr
                JOIN waste_reasons reason
                  ON reason.id = wr.waste_reason_id
                JOIN stock_items item
                  ON item.id = wr.stock_item_id
                JOIN stock_locations stock_location
                  ON stock_location.id = wr.stock_location_id
                JOIN locations location
                  ON location.id = stock_location.location_id
                JOIN campuses campus
                  ON campus.id = location.campus_id
                WHERE wr.id = ?
                  AND item.organization_id = ?
                  AND reason.organization_id = ?
                  AND campus.organization_id = ?
                """,
                (resultSet, rowNumber) ->
                    response(
                        resultSet,
                        replayed
                    ),
                wasteRecordId,
                organizationId,
                organizationId,
                organizationId
            );

        if (rows.size() > 1) {
            throw new WasteConflictException(
                "Multiple waste records matched one identifier."
            );
        }

        return rows.isEmpty()
            ? null
            : rows.get(0);
    }

    private WasteRecordResponse response(
        java.sql.ResultSet resultSet,
        boolean replayed
    ) throws java.sql.SQLException {

        return new WasteRecordResponse(
            resultSet.getObject(
                "id",
                UUID.class
            ),
            resultSet.getObject(
                "stock_item_id",
                UUID.class
            ),
            resultSet.getObject(
                "stock_location_id",
                UUID.class
            ),
            resultSet.getBigDecimal(
                "quantity"
            ),
            MeasurementUnit.valueOf(
                resultSet.getString(
                    "unit"
                )
            ),
            resultSet.getObject(
                "waste_reason_id",
                UUID.class
            ),
            resultSet.getString(
                "waste_reason_code"
            ),
            WasteCategory.valueOf(
                resultSet.getString(
                    "waste_category"
                )
            ),
            resultSet.getBigDecimal(
                "estimated_cost"
            ),
            resultSet.getString(
                "notes"
            ),
            resultSet.getObject(
                "product_id",
                UUID.class
            ),
            resultSet.getObject(
                "order_item_id",
                UUID.class
            ),
            resultSet.getObject(
                "production_run_item_id",
                UUID.class
            ),
            resultSet.getObject(
                "inventory_movement_id",
                UUID.class
            ),
            resultSet.getObject(
                "recorded_by",
                UUID.class
            ),
            resultSet.getObject(
                "recorded_at",
                OffsetDateTime.class
            ),
            replayed
        );
    }

    private WasteRecordResponse replay(
        WasteRecordResponse stored
    ) {

        return new WasteRecordResponse(
            stored.id(),
            stored.stockItemId(),
            stored.stockLocationId(),
            stored.quantity(),
            stored.unit(),
            stored.wasteReasonId(),
            stored.wasteReasonCode(),
            stored.category(),
            stored.estimatedCost(),
            stored.notes(),
            stored.productId(),
            stored.orderItemId(),
            stored.productionRunItemId(),
            stored.inventoryMovementId(),
            stored.recordedBy(),
            stored.recordedAt(),
            true
        );
    }

    private boolean samePayload(
        WasteRecordResponse stored,
        RecordWasteCommand requested,
        BigDecimal quantity,
        BigDecimal estimatedCost,
        String notes,
        UUID resolvedProductId
    ) {

        return stored.stockItemId()
            .equals(
                requested.stockItemId()
            )
            && stored.stockLocationId()
                .equals(
                    requested.stockLocationId()
                )
            && sameDecimal(
                stored.quantity(),
                quantity
            )
            && stored.unit()
                == requested.unit()
            && stored.wasteReasonId()
                .equals(
                    requested.wasteReasonId()
                )
            && sameDecimal(
                stored.estimatedCost(),
                estimatedCost
            )
            && Objects.equals(
                stored.notes(),
                notes
            )
            && Objects.equals(
                stored.productId(),
                resolvedProductId
            )
            && Objects.equals(
                stored.orderItemId(),
                requested.orderItemId()
            )
            && Objects.equals(
                stored.productionRunItemId(),
                requested.productionRunItemId()
            );
    }

    private void validateCommand(
        RecordWasteCommand command
    ) {

        if (command == null) {
            throw new WasteValidationException(
                "Waste payload is required."
            );
        }

        requireId(
            command.stockItemId(),
            "Stock item id"
        );

        requireId(
            command.stockLocationId(),
            "Stock location id"
        );

        requireId(
            command.wasteReasonId(),
            "Waste reason id"
        );

        if (command.unit() == null) {
            throw new WasteValidationException(
                "Waste unit is required."
            );
        }

        quantity(
            command.quantity()
        );

        money(
            command.estimatedCost(),
            "Estimated waste cost"
        );
    }

    private BigDecimal quantity(
        BigDecimal value
    ) {

        if (
            value == null
            || value.signum() <= 0
        ) {
            throw new WasteValidationException(
                "Waste quantity must be greater than zero."
            );
        }

        BigDecimal normalized =
            value.stripTrailingZeros();

        if (normalized.scale() > 3) {
            throw new WasteValidationException(
                "Waste quantity supports at most 3 decimal places."
            );
        }

        return value.setScale(
            3
        );
    }

    private BigDecimal money(
        BigDecimal value,
        String label
    ) {

        if (value == null) {
            return null;
        }

        if (value.signum() < 0) {
            throw new WasteValidationException(
                label + " cannot be negative."
            );
        }

        BigDecimal normalized =
            value.stripTrailingZeros();

        if (normalized.scale() > 2) {
            throw new WasteValidationException(
                label + " supports at most 2 decimal places."
            );
        }

        return value.setScale(
            2
        );
    }

    private String nullableText(
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

    private boolean sameDecimal(
        BigDecimal first,
        BigDecimal second
    ) {

        if (
            first == null
            || second == null
        ) {
            return first == null
                && second == null;
        }

        return first.compareTo(
            second
        ) == 0;
    }

    private UUID organizationId(
        UUID actorId
    ) {

        requireId(
            actorId,
            "Actor id"
        );

        List<UUID> organizations =
            jdbcTemplate.query(
                """
                SELECT organization_id
                FROM users
                WHERE id = ?
                """,
                (resultSet, rowNumber) ->
                    resultSet.getObject(
                        "organization_id",
                        UUID.class
                    ),
                actorId
            );

        if (organizations.size() != 1) {
            throw new BadCredentialsException(
                "Authenticated user does not exist."
            );
        }

        return organizations.get(0);
    }

    private void requireId(
        UUID value,
        String label
    ) {

        if (value == null) {
            throw new WasteValidationException(
                label + " is required."
            );
        }
    }

    private record ReasonContext(
        UUID id,
        String code,
        WasteCategory category,
        boolean requiresComment
    ) {
    }

    private record StockContext(
        UUID id,
        UUID effectiveProductId,
        UUID variantId,
        UUID ingredientId,
        MeasurementUnit unit
    ) {
    }

    private record OrderItemContext(
        UUID productId,
        UUID variantId
    ) {
    }

    private record ProductionContext(
        UUID id,
        UUID productId,
        UUID variantId,
        BigDecimal preparedQuantity,
        MeasurementUnit unit,
        String status
    ) {
    }

    private record BalanceContext(
        BigDecimal physicalQuantity,
        BigDecimal reservedQuantity
    ) {
    }

    private record LotContext(
        UUID id,
        BigDecimal quantityRemaining
    ) {
    }

    private record LotAllocation(
        UUID lotId,
        BigDecimal quantity
    ) {
    }
}