package com.sup2i.food.production.service;

import com.sup2i.food.common.domain.MeasurementUnit;
import com.sup2i.food.production.api.dto.CreateProductionRunCommand;
import com.sup2i.food.production.api.dto.CreateProductionRunItemCommand;
import com.sup2i.food.production.api.dto.ProductionRunItemResponse;
import com.sup2i.food.production.api.dto.ProductionRunResponse;
import com.sup2i.food.production.domain.ProductionRunStatus;
import com.sup2i.food.production.domain.ProductionTargetSource;
import com.sup2i.food.production.domain.ProductionType;
import com.sup2i.food.production.exception.ProductionConflictException;
import com.sup2i.food.production.exception.ProductionNotFoundException;
import com.sup2i.food.production.exception.ProductionValidationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class ProductionService {

    private static final BigDecimal ZERO =
        BigDecimal.ZERO;

    private final JdbcTemplate jdbcTemplate;

    private final ProductionInventoryConsumptionService
        inventoryConsumptionService;

    public ProductionService(
        JdbcTemplate jdbcTemplate,
        ProductionInventoryConsumptionService
            inventoryConsumptionService
    ) {
        this.jdbcTemplate =
            jdbcTemplate;

        this.inventoryConsumptionService =
            inventoryConsumptionService;
    }

    @Transactional
    public ProductionRunResponse createRun(
        UUID actorId,
        CreateProductionRunCommand command
    ) {

        if (command == null) {
            throw new ProductionValidationException(
                "Production command is required."
            );
        }

        UUID organizationId =
            organizationId(actorId);

        validateRequiredFields(
            command
        );

        validateCampus(
            organizationId,
            command.campusId()
        );

        validateLocation(
            organizationId,
            command.campusId(),
            command.serviceLocationId(),
            "Service location"
        );

        validateLocation(
            organizationId,
            command.campusId(),
            command.kitchenLocationId(),
            "Kitchen location"
        );

        validateOptionalCanteenMenu(
            organizationId,
            command.canteenMenuId()
        );

        validateOptionalCampusEvent(
            organizationId,
            command.campusEventId()
        );

        validateItems(
            organizationId,
            command.items(),
            command.productionDate()
        );

        UUID runId =
            UUID.randomUUID();

        OffsetDateTime now =
            OffsetDateTime.now();

        try {

            jdbcTemplate.update(
                """
                INSERT INTO production_runs (
                    id,
                    organization_id,
                    campus_id,
                    service_location_id,
                    kitchen_location_id,
                    canteen_menu_id,
                    campus_event_id,
                    production_date,
                    production_type,
                    status,
                    target_source,
                    created_by,
                    notes,
                    created_at,
                    updated_at
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    'PLANNED',
                    ?,
                    ?,
                    ?,
                    ?,
                    ?
                )
                """,
                runId,
                organizationId,
                command.campusId(),
                command.serviceLocationId(),
                command.kitchenLocationId(),
                command.canteenMenuId(),
                command.campusEventId(),
                command.productionDate(),
                command.productionType().name(),
                command.targetSource().name(),
                actorId,
                normalizeNullableText(
                    command.notes()
                ),
                now,
                now
            );

            for (
                CreateProductionRunItemCommand item
                    : command.items()
            ) {

                insertItem(
                    runId,
                    item,
                    now
                );
            }

        } catch (
            DataIntegrityViolationException exception
        ) {

            throw new ProductionConflictException(
                "Production run conflicts with an existing resource or database invariant."
            );
        }

        return getRun(
            actorId,
            runId
        );
    }

    @Transactional
    public ProductionRunResponse startRun(
        UUID actorId,
        UUID productionRunId
    ) {

        UUID organizationId =
            organizationId(actorId);

        if (productionRunId == null) {
            throw new ProductionValidationException(
                "Production run id is required."
            );
        }

        List<StartContext> contexts =
            jdbcTemplate.query(
                """
                SELECT
                    pr.status,
                    pr.kitchen_location_id
                FROM production_runs pr
                WHERE pr.id = ?
                  AND pr.organization_id = ?
                FOR UPDATE
                """,
                (resultSet, rowNumber) ->
                    new StartContext(
                        ProductionRunStatus.valueOf(
                            resultSet.getString(
                                "status"
                            )
                        ),
                        resultSet.getObject(
                            "kitchen_location_id",
                            UUID.class
                        )
                    ),
                productionRunId,
                organizationId
            );

        if (contexts.isEmpty()) {
            throw new ProductionNotFoundException(
                "Production run does not exist."
            );
        }

        if (contexts.size() != 1) {
            throw new ProductionConflictException(
                "Multiple production runs matched one id."
            );
        }

        StartContext context =
            contexts.get(0);

        if (
            context.status()
                == ProductionRunStatus.IN_PROGRESS
        ) {
            return getRun(
                actorId,
                productionRunId
            );
        }

        if (
            context.status()
                != ProductionRunStatus.PLANNED
        ) {
            throw new ProductionConflictException(
                "Only a planned production run can be started."
            );
        }

        OffsetDateTime now =
            OffsetDateTime.now();

        inventoryConsumptionService.consume(
            actorId,
            organizationId,
            productionRunId,
            context.kitchenLocationId(),
            now
        );

        int itemUpdates =
            jdbcTemplate.update(
                """
                UPDATE production_run_items
                SET
                    preparation_started_at =
                        COALESCE(
                            preparation_started_at,
                            ?
                        ),
                    updated_at = ?
                WHERE production_run_id = ?
                """,
                now,
                now,
                productionRunId
            );

        if (itemUpdates <= 0) {
            throw new ProductionConflictException(
                "Production run has no items to start."
            );
        }

        int runUpdates =
            jdbcTemplate.update(
                """
                UPDATE production_runs
                SET
                    status = 'IN_PROGRESS',
                    started_at = ?,
                    updated_at = ?
                WHERE id = ?
                  AND organization_id = ?
                  AND status = 'PLANNED'
                """,
                now,
                now,
                productionRunId,
                organizationId
            );

        if (runUpdates != 1) {
            throw new ProductionConflictException(
                "Production run changed concurrently while starting."
            );
        }

        return getRun(
            actorId,
            productionRunId
        );
    }

    @Transactional
    public ProductionRunResponse completeRun(
        UUID actorId,
        UUID productionRunId,
        java.util.Map<
            UUID,
            java.math.BigDecimal
        > preparedQuantities
    ) {

        UUID organizationId =
            organizationId(actorId);

        if (productionRunId == null) {
            throw new ProductionValidationException(
                "Production run id is required."
            );
        }

        if (
            preparedQuantities == null
            || preparedQuantities.isEmpty()
        ) {
            throw new ProductionValidationException(
                "Actual prepared quantities are required."
            );
        }

        List<ProductionRunStatus> statuses =
            jdbcTemplate.query(
                """
                SELECT status
                FROM production_runs
                WHERE id = ?
                  AND organization_id = ?
                FOR UPDATE
                """,
                (resultSet, rowNumber) ->
                    ProductionRunStatus.valueOf(
                        resultSet.getString(
                            "status"
                        )
                    ),
                productionRunId,
                organizationId
            );

        if (statuses.isEmpty()) {
            throw new ProductionNotFoundException(
                "Production run does not exist."
            );
        }

        if (statuses.size() != 1) {
            throw new ProductionConflictException(
                "Multiple production runs matched one id."
            );
        }

        ProductionRunStatus status =
            statuses.get(0);

        List<CompletionItem> items =
            jdbcTemplate.query(
                """
                SELECT
                    id,
                    prepared_quantity
                FROM production_run_items
                WHERE production_run_id = ?
                ORDER BY id
                FOR UPDATE
                """,
                (resultSet, rowNumber) ->
                    new CompletionItem(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getBigDecimal(
                            "prepared_quantity"
                        )
                    ),
                productionRunId
            );

        if (items.isEmpty()) {
            throw new ProductionConflictException(
                "Production run has no items."
            );
        }

        if (
            preparedQuantities.size()
                != items.size()
        ) {
            throw new ProductionValidationException(
                "Actual prepared quantities must contain exactly one value for every production run item."
            );
        }

        for (
            CompletionItem item
            : items
        ) {

            if (
                !preparedQuantities.containsKey(
                    item.id()
                )
            ) {
                throw new ProductionValidationException(
                    "Actual prepared quantities contain an unknown or missing production run item."
                );
            }

            java.math.BigDecimal quantity =
                preparedQuantities.get(
                    item.id()
                );

            if (quantity == null) {
                throw new ProductionValidationException(
                    "Actual prepared quantity cannot be null."
                );
            }

            if (quantity.signum() < 0) {
                throw new ProductionValidationException(
                    "Actual prepared quantity cannot be negative."
                );
            }
        }

        if (
            status
                == ProductionRunStatus.COMPLETED
        ) {

            for (
                CompletionItem item
                : items
            ) {

                java.math.BigDecimal requested =
                    preparedQuantities.get(
                        item.id()
                    );

                if (
                    item.preparedQuantity()
                        .compareTo(
                            requested
                        ) != 0
                ) {
                    throw new ProductionConflictException(
                        "Completed production run cannot be replayed with different actual prepared quantities."
                    );
                }
            }

            return getRun(
                actorId,
                productionRunId
            );
        }

        if (
            status
                != ProductionRunStatus.IN_PROGRESS
        ) {
            throw new ProductionConflictException(
                "Only an in-progress production run can be completed."
            );
        }

        OffsetDateTime now =
            OffsetDateTime.now();

        for (
            CompletionItem item
            : items
        ) {

            java.math.BigDecimal quantity =
                preparedQuantities.get(
                    item.id()
                );

            int itemUpdates =
                jdbcTemplate.update(
                    """
                    UPDATE production_run_items
                    SET
                        prepared_quantity = ?,
                        preparation_completed_at = ?,
                        updated_at = ?
                    WHERE id = ?
                      AND production_run_id = ?
                    """,
                    quantity,
                    now,
                    now,
                    item.id(),
                    productionRunId
                );

            if (itemUpdates != 1) {
                throw new ProductionConflictException(
                    "Production run item changed concurrently while completing."
                );
            }
        }

        int runUpdates =
            jdbcTemplate.update(
                """
                UPDATE production_runs
                SET
                    status = 'COMPLETED',
                    completed_at = ?,
                    approved_by = ?,
                    updated_at = ?
                WHERE id = ?
                  AND organization_id = ?
                  AND status = 'IN_PROGRESS'
                """,
                now,
                actorId,
                now,
                productionRunId,
                organizationId
            );

        if (runUpdates != 1) {
            throw new ProductionConflictException(
                "Production run changed concurrently while completing."
            );
        }

        return getRun(
            actorId,
            productionRunId
        );
    }

    @Transactional
    public ProductionRunResponse cancelRun(
        UUID actorId,
        UUID productionRunId
    ) {

        UUID organizationId =
            organizationId(actorId);

        if (productionRunId == null) {
            throw new ProductionValidationException(
                "Production run id is required."
            );
        }

        List<ProductionRunStatus> statuses =
            jdbcTemplate.query(
                """
                SELECT status
                FROM production_runs
                WHERE id = ?
                  AND organization_id = ?
                FOR UPDATE
                """,
                (resultSet, rowNumber) ->
                    ProductionRunStatus.valueOf(
                        resultSet.getString(
                            "status"
                        )
                    ),
                productionRunId,
                organizationId
            );

        if (statuses.isEmpty()) {
            throw new ProductionNotFoundException(
                "Production run does not exist."
            );
        }

        if (statuses.size() != 1) {
            throw new ProductionConflictException(
                "Multiple production runs matched one id."
            );
        }

        ProductionRunStatus status =
            statuses.get(0);

        if (
            status
                == ProductionRunStatus.CANCELLED
        ) {
            return getRun(
                actorId,
                productionRunId
            );
        }

        if (
            status
                != ProductionRunStatus.PLANNED
        ) {
            throw new ProductionConflictException(
                "Only a planned production run can be cancelled. Started production requires an explicit return or waste workflow before cancellation."
            );
        }

        OffsetDateTime now =
            OffsetDateTime.now();

        int runUpdates =
            jdbcTemplate.update(
                """
                UPDATE production_runs
                SET
                    status = 'CANCELLED',
                    cancelled_at = ?,
                    updated_at = ?
                WHERE id = ?
                  AND organization_id = ?
                  AND status = 'PLANNED'
                """,
                now,
                now,
                productionRunId,
                organizationId
            );

        if (runUpdates != 1) {
            throw new ProductionConflictException(
                "Production run changed concurrently while cancelling."
            );
        }

        return getRun(
            actorId,
            productionRunId
        );
    }

    @Transactional(readOnly = true)
    public ProductionRunResponse getRun(
        UUID actorId,
        UUID productionRunId
    ) {

        UUID organizationId =
            organizationId(actorId);

        if (productionRunId == null) {
            throw new ProductionValidationException(
                "Production run id is required."
            );
        }

        List<ProductionRunResponse> runs =
            jdbcTemplate.query(
                """
                SELECT
                    pr.id,
                    pr.organization_id,
                    pr.campus_id,
                    pr.service_location_id,
                    pr.kitchen_location_id,
                    pr.canteen_menu_id,
                    pr.campus_event_id,
                    pr.production_date,
                    pr.production_type,
                    pr.status,
                    pr.target_source,
                    pr.started_at,
                    pr.completed_at,
                    pr.cancelled_at,
                    pr.created_by,
                    pr.approved_by,
                    pr.notes,
                    pr.created_at,
                    pr.updated_at
                FROM production_runs pr
                WHERE pr.id = ?
                  AND pr.organization_id = ?
                """,
                (resultSet, rowNumber) ->
                    mapRun(
                        resultSet,
                        actorId
                    ),
                productionRunId,
                organizationId
            );

        if (runs.isEmpty()) {
            throw new ProductionNotFoundException(
                "Production run does not exist."
            );
        }

        if (runs.size() != 1) {
            throw new ProductionConflictException(
                "Multiple production runs matched one id."
            );
        }

        return runs.get(0);
    }

    @Transactional(readOnly = true)
    public List<ProductionRunResponse> listRuns(
        UUID actorId,
        UUID campusId,
        LocalDate productionDate,
        ProductionRunStatus status
    ) {

        UUID organizationId =
            organizationId(actorId);

        StringBuilder sql =
            new StringBuilder(
                """
                SELECT
                    pr.id,
                    pr.organization_id,
                    pr.campus_id,
                    pr.service_location_id,
                    pr.kitchen_location_id,
                    pr.canteen_menu_id,
                    pr.campus_event_id,
                    pr.production_date,
                    pr.production_type,
                    pr.status,
                    pr.target_source,
                    pr.started_at,
                    pr.completed_at,
                    pr.cancelled_at,
                    pr.created_by,
                    pr.approved_by,
                    pr.notes,
                    pr.created_at,
                    pr.updated_at
                FROM production_runs pr
                WHERE pr.organization_id = ?
                """
            );

        java.util.ArrayList<Object> parameters =
            new java.util.ArrayList<>();

        parameters.add(
            organizationId
        );

        if (campusId != null) {

            sql.append(
                " AND pr.campus_id = ?"
            );

            parameters.add(
                campusId
            );
        }

        if (productionDate != null) {

            sql.append(
                " AND pr.production_date = ?"
            );

            parameters.add(
                productionDate
            );
        }

        if (status != null) {

            sql.append(
                " AND pr.status = ?"
            );

            parameters.add(
                status.name()
            );
        }

        sql.append(
            " ORDER BY pr.production_date DESC, pr.created_at DESC, pr.id"
        );

        Object[] arguments =
            parameters.toArray();

        return jdbcTemplate.query(
            sql.toString(),
            (resultSet, rowNumber) ->
                mapRun(
                    resultSet,
                    actorId
                ),
            arguments
        );
    }

    private void insertItem(
        UUID runId,
        CreateProductionRunItemCommand item,
        OffsetDateTime now
    ) {

        jdbcTemplate.update(
            """
            INSERT INTO production_run_items (
                id,
                production_run_id,
                product_id,
                variant_id,
                recipe_id,
                target_quantity,
                prepared_quantity,
                unit,
                estimated_unit_cost,
                notes,
                created_at,
                updated_at
            )
            VALUES (
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                0,
                ?,
                ?,
                ?,
                ?,
                ?
            )
            """,
            UUID.randomUUID(),
            runId,
            item.productId(),
            item.variantId(),
            item.recipeId(),
            item.targetQuantity(),
            item.unit().name(),
            item.estimatedUnitCost(),
            normalizeNullableText(
                item.notes()
            ),
            now,
            now
        );
    }

    private ProductionRunResponse mapRun(
        ResultSet resultSet,
        UUID actorId
    ) throws SQLException {

        UUID runId =
            resultSet.getObject(
                "id",
                UUID.class
            );

        return new ProductionRunResponse(
            runId,
            resultSet.getObject(
                "organization_id",
                UUID.class
            ),
            resultSet.getObject(
                "campus_id",
                UUID.class
            ),
            resultSet.getObject(
                "service_location_id",
                UUID.class
            ),
            resultSet.getObject(
                "kitchen_location_id",
                UUID.class
            ),
            resultSet.getObject(
                "canteen_menu_id",
                UUID.class
            ),
            resultSet.getObject(
                "campus_event_id",
                UUID.class
            ),
            resultSet.getObject(
                "production_date",
                LocalDate.class
            ),
            ProductionType.valueOf(
                resultSet.getString(
                    "production_type"
                )
            ),
            ProductionRunStatus.valueOf(
                resultSet.getString(
                    "status"
                )
            ),
            ProductionTargetSource.valueOf(
                resultSet.getString(
                    "target_source"
                )
            ),
            resultSet.getObject(
                "started_at",
                OffsetDateTime.class
            ),
            resultSet.getObject(
                "completed_at",
                OffsetDateTime.class
            ),
            resultSet.getObject(
                "cancelled_at",
                OffsetDateTime.class
            ),
            resultSet.getObject(
                "created_by",
                UUID.class
            ),
            resultSet.getObject(
                "approved_by",
                UUID.class
            ),
            resultSet.getString(
                "notes"
            ),
            resultSet.getObject(
                "created_at",
                OffsetDateTime.class
            ),
            resultSet.getObject(
                "updated_at",
                OffsetDateTime.class
            ),
            findItems(
                actorId,
                runId
            )
        );
    }

    private List<ProductionRunItemResponse> findItems(
        UUID actorId,
        UUID runId
    ) {

        UUID organizationId =
            organizationId(actorId);

        return jdbcTemplate.query(
            """
            SELECT
                pri.id,
                pri.product_id,
                pri.variant_id,
                pri.recipe_id,
                pri.target_quantity,
                pri.prepared_quantity,
                pri.unit,
                pri.estimated_unit_cost,
                pri.preparation_started_at,
                pri.preparation_completed_at,
                pri.notes
            FROM production_run_items pri
            JOIN production_runs pr
              ON pr.id = pri.production_run_id
            WHERE pri.production_run_id = ?
              AND pr.organization_id = ?
            ORDER BY pri.created_at, pri.id
            """,
            (resultSet, rowNumber) ->
                new ProductionRunItemResponse(
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
                    resultSet.getObject(
                        "recipe_id",
                        UUID.class
                    ),
                    resultSet.getBigDecimal(
                        "target_quantity"
                    ),
                    resultSet.getBigDecimal(
                        "prepared_quantity"
                    ),
                    MeasurementUnit.valueOf(
                        resultSet.getString(
                            "unit"
                        )
                    ),
                    resultSet.getBigDecimal(
                        "estimated_unit_cost"
                    ),
                    resultSet.getObject(
                        "preparation_started_at",
                        OffsetDateTime.class
                    ),
                    resultSet.getObject(
                        "preparation_completed_at",
                        OffsetDateTime.class
                    ),
                    resultSet.getString(
                        "notes"
                    )
                ),
            runId,
            organizationId
        );
    }

    private UUID organizationId(
        UUID actorId
    ) {

        if (actorId == null) {
            throw new ProductionValidationException(
                "Actor id is required."
            );
        }

        try {

            UUID organizationId =
                jdbcTemplate.queryForObject(
                    """
                    SELECT organization_id
                    FROM users
                    WHERE id = ?
                    """,
                    UUID.class,
                    actorId
                );

            if (organizationId == null) {
                throw new ProductionNotFoundException(
                    "Authenticated actor does not belong to an organization."
                );
            }

            return organizationId;

        } catch (
            EmptyResultDataAccessException exception
        ) {

            throw new ProductionNotFoundException(
                "Authenticated actor does not exist."
            );
        }
    }

    private void validateRequiredFields(
        CreateProductionRunCommand command
    ) {

        if (command.campusId() == null) {
            throw new ProductionValidationException(
                "Campus is required."
            );
        }

        if (command.serviceLocationId() == null) {
            throw new ProductionValidationException(
                "Service location is required."
            );
        }

        if (command.kitchenLocationId() == null) {
            throw new ProductionValidationException(
                "Kitchen location is required."
            );
        }

        if (command.productionDate() == null) {
            throw new ProductionValidationException(
                "Production date is required."
            );
        }

        if (command.productionType() == null) {
            throw new ProductionValidationException(
                "Production type is required."
            );
        }

        if (command.targetSource() == null) {
            throw new ProductionValidationException(
                "Production target source is required."
            );
        }

        if (
            command.items() == null
            || command.items().isEmpty()
        ) {
            throw new ProductionValidationException(
                "At least one production item is required."
            );
        }
    }

    private void validateCampus(
        UUID organizationId,
        UUID campusId
    ) {

        if (
            !exists(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM campuses c
                    WHERE c.id = ?
                      AND c.organization_id = ?
                      AND c.is_active = TRUE
                )
                """,
                campusId,
                organizationId
            )
        ) {
            throw new ProductionNotFoundException(
                "Campus does not exist or is inactive."
            );
        }
    }

    private void validateLocation(
        UUID organizationId,
        UUID campusId,
        UUID locationId,
        String label
    ) {

        if (
            !exists(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM locations l
                    JOIN campuses c
                      ON c.id = l.campus_id
                    WHERE l.id = ?
                      AND l.campus_id = ?
                      AND c.organization_id = ?
                      AND l.is_active = TRUE
                      AND c.is_active = TRUE
                )
                """,
                locationId,
                campusId,
                organizationId
            )
        ) {
            throw new ProductionNotFoundException(
                label
                    + " does not exist in the selected campus or is inactive."
            );
        }
    }

    private void validateOptionalCanteenMenu(
        UUID organizationId,
        UUID canteenMenuId
    ) {

        if (canteenMenuId == null) {
            return;
        }

        if (
            !exists(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM canteen_menus cm
                    JOIN campuses c
                      ON c.id = cm.campus_id
                    WHERE cm.id = ?
                      AND c.organization_id = ?
                )
                """,
                canteenMenuId,
                organizationId
            )
        ) {
            throw new ProductionNotFoundException(
                "Canteen menu does not exist."
            );
        }
    }

    private void validateOptionalCampusEvent(
        UUID organizationId,
        UUID campusEventId
    ) {

        if (campusEventId == null) {
            return;
        }

        if (
            !exists(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM campus_events ce
                    JOIN campuses c
                      ON c.id = ce.campus_id
                    WHERE ce.id = ?
                      AND c.organization_id = ?
                )
                """,
                campusEventId,
                organizationId
            )
        ) {
            throw new ProductionNotFoundException(
                "Campus event does not exist."
            );
        }
    }

    private void validateItems(
        UUID organizationId,
        List<CreateProductionRunItemCommand> items,
        LocalDate productionDate
    ) {

        Set<ItemKey> keys =
            new HashSet<>();

        for (
            CreateProductionRunItemCommand item
                : items
        ) {

            if (item == null) {
                throw new ProductionValidationException(
                    "Production item is required."
                );
            }

            validateItemNumbers(
                item
            );

            validateProduct(
                organizationId,
                item.productId()
            );

            validateVariant(
                organizationId,
                item.productId(),
                item.variantId()
            );

            validateRecipe(
                organizationId,
                item,
                productionDate
            );

            ItemKey key =
                new ItemKey(
                    item.productId(),
                    item.variantId()
                );

            if (!keys.add(key)) {
                throw new ProductionConflictException(
                    "A production run cannot contain the same product and variant twice."
                );
            }
        }
    }

    private void validateItemNumbers(
        CreateProductionRunItemCommand item
    ) {

        if (item.productId() == null) {
            throw new ProductionValidationException(
                "Production item product is required."
            );
        }

        if (item.targetQuantity() == null) {
            throw new ProductionValidationException(
                "Production item target quantity is required."
            );
        }

        if (
            item.targetQuantity()
                .compareTo(ZERO)
                < 0
        ) {
            throw new ProductionValidationException(
                "Production item target quantity cannot be negative."
            );
        }

        if (item.unit() == null) {
            throw new ProductionValidationException(
                "Production item unit is required."
            );
        }

        if (
            item.estimatedUnitCost() != null
            && item.estimatedUnitCost()
                .compareTo(ZERO)
                < 0
        ) {
            throw new ProductionValidationException(
                "Estimated unit cost cannot be negative."
            );
        }
    }

    private void validateProduct(
        UUID organizationId,
        UUID productId
    ) {

        if (
            !exists(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM products p
                    WHERE p.id = ?
                      AND p.organization_id = ?
                      AND p.is_active = TRUE
                )
                """,
                productId,
                organizationId
            )
        ) {
            throw new ProductionNotFoundException(
                "Production product does not exist or is inactive."
            );
        }
    }

    private void validateVariant(
        UUID organizationId,
        UUID productId,
        UUID variantId
    ) {

        if (variantId == null) {
            return;
        }

        if (
            !exists(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM product_variants pv
                    JOIN products p
                      ON p.id = pv.product_id
                    WHERE pv.id = ?
                      AND pv.product_id = ?
                      AND p.organization_id = ?
                )
                """,
                variantId,
                productId,
                organizationId
            )
        ) {
            throw new ProductionNotFoundException(
                "Production variant does not belong to the selected product."
            );
        }
    }

    private void validateRecipe(
        UUID organizationId,
        CreateProductionRunItemCommand item,
        LocalDate productionDate
    ) {

        Boolean prepared =
            jdbcTemplate.queryForObject(
                """
                SELECT p.is_prepared
                FROM products p
                WHERE p.id = ?
                  AND p.organization_id = ?
                """,
                Boolean.class,
                item.productId(),
                organizationId
            );

        boolean preparedProduct =
            Boolean.TRUE.equals(
                prepared
            );

        if (
            preparedProduct
            && item.recipeId() == null
        ) {
            throw new ProductionValidationException(
                "Prepared production items require a recipe."
            );
        }

        if (item.recipeId() == null) {
            return;
        }

        boolean recipeExists;

        if (item.variantId() == null) {

            recipeExists =
                exists(
                    """
                    SELECT EXISTS (
                        SELECT 1
                        FROM recipes r
                        JOIN products p
                          ON p.id = r.product_id
                        WHERE r.id = ?
                          AND r.product_id = ?
                          AND r.variant_id IS NULL
                          AND p.organization_id = ?
                          AND r.is_active = TRUE
                          AND r.effective_from <= ?
                          AND (
                              r.effective_to IS NULL
                              OR r.effective_to > ?
                          )
                    )
                    """,
                    item.recipeId(),
                    item.productId(),
                    organizationId,
                    productionDate.atStartOfDay()
                        .atOffset(
                            java.time.ZoneOffset.UTC
                        ),
                    productionDate.atStartOfDay()
                        .atOffset(
                            java.time.ZoneOffset.UTC
                        )
                );
        }
        else {

            recipeExists =
                exists(
                    """
                    SELECT EXISTS (
                        SELECT 1
                        FROM recipes r
                        JOIN products p
                          ON p.id = r.product_id
                        WHERE r.id = ?
                          AND r.product_id = ?
                          AND r.variant_id = ?
                          AND p.organization_id = ?
                          AND r.is_active = TRUE
                          AND r.effective_from <= ?
                          AND (
                              r.effective_to IS NULL
                              OR r.effective_to > ?
                          )
                    )
                    """,
                    item.recipeId(),
                    item.productId(),
                    item.variantId(),
                    organizationId,
                    productionDate.atStartOfDay()
                        .atOffset(
                            java.time.ZoneOffset.UTC
                        ),
                    productionDate.atStartOfDay()
                        .atOffset(
                            java.time.ZoneOffset.UTC
                        )
                );
        }

        if (!recipeExists) {
            throw new ProductionNotFoundException(
                "Recipe does not match the production product, variant, tenant, or effective period."
            );
        }
    }

    private boolean exists(
        String sql,
        Object... arguments
    ) {

        Boolean result =
            jdbcTemplate.queryForObject(
                sql,
                Boolean.class,
                arguments
            );

        return Boolean.TRUE.equals(
            result
        );
    }

    private String normalizeNullableText(
        String value
    ) {

        if (value == null) {
            return null;
        }

        String normalized =
            value.trim();

        if (normalized.isEmpty()) {
            return null;
        }

        return normalized;
    }

    private record CompletionItem(
        UUID id,
        java.math.BigDecimal preparedQuantity
    ) {
    }

    private record StartContext(
        ProductionRunStatus status,
        UUID kitchenLocationId
    ) {
    }

    private record ItemKey(
        UUID productId,
        UUID variantId
    ) {

        private ItemKey {

            Objects.requireNonNull(
                productId
            );
        }
    }
}