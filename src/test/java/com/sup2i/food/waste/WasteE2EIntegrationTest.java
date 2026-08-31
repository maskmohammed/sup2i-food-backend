package com.sup2i.food.waste;

import com.sup2i.food.common.domain.MeasurementUnit;
import com.sup2i.food.waste.api.dto.RecordWasteCommand;
import com.sup2i.food.waste.api.dto.WasteReasonCommand;
import com.sup2i.food.waste.api.dto.WasteReasonResponse;
import com.sup2i.food.waste.api.dto.WasteRecordResponse;
import com.sup2i.food.waste.domain.WasteCategory;
import com.sup2i.food.waste.exception.WasteConflictException;
import com.sup2i.food.waste.exception.WasteNotFoundException;
import com.sup2i.food.waste.exception.WasteValidationException;
import com.sup2i.food.waste.service.WasteReasonService;
import com.sup2i.food.waste.service.WasteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
    properties = {
        "sup2i.security.jwt.issuer=sup2i-food-backend",
        "sup2i.security.jwt.secret-base64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=",
        "sup2i.security.mfa.encryption-key-base64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk="
    }
)
@ActiveProfiles("test")
@Testcontainers
class WasteE2EIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
        new PostgreSQLContainer(
            "postgres:17.10-bookworm"
        )
            .withDatabaseName(
                "sup2i_food_waste_test"
            );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WasteReasonService wasteReasonService;

    @Autowired
    private WasteService wasteService;

    private UUID organizationId;
    private UUID actorId;
    private UUID campusId;
    private UUID stockLocationId;
    private UUID productId;
    private UUID stockItemId;
    private UUID wasteReasonId;

    @BeforeEach
    void setup() {

        organizationId =
            organization(
                "WASTE"
            );

        actorId =
            user(
                organizationId,
                "ACTOR"
            );

        campusId =
            campus(
                organizationId,
                "MAIN"
            );

        UUID locationId =
            location(
                campusId,
                "STORAGE",
                "STORAGE"
            );

        stockLocationId =
            stockLocation(
                locationId
            );

        UUID categoryId =
            category(
                organizationId
            );

        productId =
            product(
                organizationId,
                categoryId
            );

        stockItemId =
            stockItem(
                organizationId,
                productId
            );

        wasteReasonId =
            UUID.randomUUID();

        wasteReasonService.create(
            actorId,
            wasteReasonId,
            new WasteReasonCommand(
                "UNSOLD-" + suffix(),
                "Unsold",
                WasteCategory.UNSOLD,
                false
            )
        );
    }

    @Test
    void finalWasteReasonCategoriesAndReplayWork() {

        UUID id =
            UUID.randomUUID();

        WasteReasonCommand command =
            new WasteReasonCommand(
                "CONTAM-" + suffix(),
                "Contamination",
                WasteCategory.CONTAMINATION,
                true
            );

        WasteReasonResponse first =
            wasteReasonService.create(
                actorId,
                id,
                command
            );

        WasteReasonResponse replay =
            wasteReasonService.create(
                actorId,
                id,
                command
            );

        assertThat(first.category())
            .isEqualTo(
                WasteCategory.CONTAMINATION
            );

        assertThat(first.requiresComment())
            .isTrue();

        assertThat(replay.id())
            .isEqualTo(id);

        assertThat(
            count(
                "waste_reasons",
                id
            )
        ).isEqualTo(1L);
    }

    @Test
    void requiredCommentRejectsMutation() {

        UUID reason =
            UUID.randomUUID();

        wasteReasonService.create(
            actorId,
            reason,
            new WasteReasonCommand(
                "DAMAGED-" + suffix(),
                "Damaged",
                WasteCategory.DAMAGED,
                true
            )
        );

        UUID waste =
            UUID.randomUUID();

        assertThatThrownBy(() ->
            wasteService.record(
                actorId,
                waste,
                command(
                    "1.000",
                    reason,
                    null,
                    null
                )
            )
        )
            .isInstanceOf(
                WasteValidationException.class
            );

        assertThat(
            count(
                "waste_records",
                waste
            )
        ).isZero();

        assertThat(
            movementCount(
                waste
            )
        ).isZero();
    }

    @Test
    void wasteUsesUnreservedStockAndFefoLots() {

        balance(
            "10.000",
            "3.000"
        );

        UUID first =
            lot(
                "4.000",
                OffsetDateTime.now()
                    .plusDays(1)
            );

        UUID second =
            lot(
                "6.000",
                OffsetDateTime.now()
                    .plusDays(2)
            );

        UUID waste =
            UUID.randomUUID();

        WasteRecordResponse result =
            wasteService.record(
                actorId,
                waste,
                command(
                    "5.000",
                    wasteReasonId,
                    "FEFO",
                    null
                )
            );

        assertThat(result.replayed())
            .isFalse();

        assertBalance(
            "5.000",
            "3.000"
        );

        assertThat(
            lotRemaining(first)
        ).isEqualByComparingTo(
            "0.000"
        );

        assertThat(
            lotRemaining(second)
        ).isEqualByComparingTo(
            "5.000"
        );

        Long movement =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM inventory_movements
                WHERE id = ?
                  AND movement_type = 'WASTE'
                  AND physical_delta = -5.000
                  AND reserved_delta = 0.000
                  AND reference_type = 'WASTE_RECORD'
                  AND reference_id = ?
                """,
                Long.class,
                result.inventoryMovementId(),
                waste
            );

        assertThat(movement)
            .isEqualTo(1L);

        Long bridges =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM inventory_movement_lots
                WHERE inventory_movement_id = ?
                """,
                Long.class,
                result.inventoryMovementId()
            );

        assertThat(bridges)
            .isEqualTo(2L);
    }

    @Test
    void replayIsExactlyOnceEvenAfterReasonDisableAndDifferentReplayConflicts() {

        balance(
            "5.000",
            "0.000"
        );

        lot(
            "5.000",
            OffsetDateTime.now()
                .plusDays(2)
        );

        UUID waste =
            UUID.randomUUID();

        RecordWasteCommand original =
            command(
                "2.000",
                wasteReasonId,
                "Replay",
                null
            );

        WasteRecordResponse first =
            wasteService.record(
                actorId,
                waste,
                original
            );

        wasteReasonService.setActive(
            actorId,
            wasteReasonId,
            false
        );

        WasteRecordResponse replay =
            wasteService.record(
                actorId,
                waste,
                original
            );

        assertThat(first.replayed())
            .isFalse();

        assertThat(replay.replayed())
            .isTrue();

        assertThatThrownBy(() ->
            wasteService.record(
                actorId,
                waste,
                command(
                    "1.000",
                    wasteReasonId,
                    "Replay",
                    null
                )
            )
        )
            .isInstanceOf(
                WasteConflictException.class
            );

        assertBalance(
            "3.000",
            "0.000"
        );

        assertThat(
            count(
                "waste_records",
                waste
            )
        ).isEqualTo(1L);

        assertThat(
            movementCount(
                waste
            )
        ).isEqualTo(1L);
    }

    @Test
    void reservedStockAndTenantIsolationAreProtected() {

        balance(
            "5.000",
            "4.000"
        );

        UUID stockLot =
            lot(
                "5.000",
                OffsetDateTime.now()
                    .plusDays(2)
            );

        UUID blocked =
            UUID.randomUUID();

        assertThatThrownBy(() ->
            wasteService.record(
                actorId,
                blocked,
                command(
                    "2.000",
                    wasteReasonId,
                    "Reserved",
                    null
                )
            )
        )
            .isInstanceOf(
                WasteConflictException.class
            );

        assertBalance(
            "5.000",
            "4.000"
        );

        assertThat(
            lotRemaining(stockLot)
        ).isEqualByComparingTo(
            "5.000"
        );

        UUID foreignOrg =
            organization(
                "FOREIGN"
            );

        UUID foreignActor =
            user(
                foreignOrg,
                "FOREIGN"
            );

        assertThatThrownBy(() ->
            wasteReasonService.get(
                foreignActor,
                wasteReasonId
            )
        )
            .isInstanceOf(
                WasteNotFoundException.class
            );
    }

    @Test
    void productionWasteCreatesTraceAndCannotExceedPreparedQuantity() {

        balance(
            "5.000",
            "0.000"
        );

        lot(
            "5.000",
            OffsetDateTime.now()
                .plusDays(2)
        );

        UUID serviceLocation =
            location(
                campusId,
                "SERVICE",
                "SNACK"
            );

        UUID kitchenLocation =
            location(
                campusId,
                "KITCHEN",
                "KITCHEN"
            );

        UUID run =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO production_runs(
                id,
                organization_id,
                campus_id,
                service_location_id,
                kitchen_location_id,
                production_date,
                production_type,
                status,
                target_source,
                started_at,
                completed_at,
                created_by
            )
            VALUES (
                ?, ?, ?, ?, ?,
                CURRENT_DATE,
                'OTHER',
                'COMPLETED',
                'MANUAL',
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP,
                ?
            )
            """,
            run,
            organizationId,
            campusId,
            serviceLocation,
            kitchenLocation,
            actorId
        );

        UUID runItem =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO production_run_items(
                id,
                production_run_id,
                product_id,
                target_quantity,
                prepared_quantity,
                unit,
                preparation_started_at,
                preparation_completed_at
            )
            VALUES (
                ?, ?, ?,
                5.000,
                5.000,
                'PIECE',
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            """,
            runItem,
            run,
            productId
        );

        UUID firstWaste =
            UUID.randomUUID();

        WasteRecordResponse result =
            wasteService.record(
                actorId,
                firstWaste,
                command(
                    "2.000",
                    wasteReasonId,
                    "Production",
                    runItem
                )
            );

        Long trace =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM production_run_movements
                WHERE production_run_item_id = ?
                  AND inventory_movement_id = ?
                  AND movement_role = 'WASTE'
                """,
                Long.class,
                runItem,
                result.inventoryMovementId()
            );

        assertThat(trace)
            .isEqualTo(1L);

        UUID exceeding =
            UUID.randomUUID();

        assertThatThrownBy(() ->
            wasteService.record(
                actorId,
                exceeding,
                command(
                    "4.000",
                    wasteReasonId,
                    "Exceed",
                    runItem
                )
            )
        )
            .isInstanceOf(
                WasteConflictException.class
            );

        assertBalance(
            "3.000",
            "0.000"
        );

        assertThat(
            count(
                "waste_records",
                exceeding
            )
        ).isZero();
    }

    private RecordWasteCommand command(
        String quantity,
        UUID reason,
        String notes,
        UUID productionRunItemId
    ) {

        return new RecordWasteCommand(
            stockItemId,
            stockLocationId,
            new BigDecimal(quantity),
            MeasurementUnit.PIECE,
            reason,
            new BigDecimal("10.00"),
            notes,
            null,
            null,
            productionRunItemId
        );
    }

    private UUID organization(
        String prefix
    ) {

        UUID id =
            UUID.randomUUID();

        String code =
            suffix();

        jdbcTemplate.update(
            """
            INSERT INTO organizations(
                id,
                name,
                code,
                is_active
            )
            VALUES (?, ?, ?, TRUE)
            """,
            id,
            prefix + " Organization " + code,
            prefix + code
        );

        return id;
    }

    private UUID user(
        UUID org,
        String prefix
    ) {

        UUID id =
            UUID.randomUUID();

        String code =
            suffix();

        jdbcTemplate.update(
            """
            INSERT INTO users(
                id,
                organization_id,
                email,
                first_name,
                last_name,
                status
            )
            VALUES (?, ?, ?, ?, ?, 'ACTIVE')
            """,
            id,
            org,
            "waste-" + code + "@sup2i.test",
            prefix,
            "User"
        );

        return id;
    }

    private UUID campus(
        UUID org,
        String prefix
    ) {

        UUID id =
            UUID.randomUUID();

        String code =
            suffix();

        jdbcTemplate.update(
            """
            INSERT INTO campuses(
                id,
                organization_id,
                name,
                code,
                is_active
            )
            VALUES (?, ?, ?, ?, TRUE)
            """,
            id,
            org,
            prefix + " Campus " + code,
            prefix + code
        );

        return id;
    }

    private UUID location(
        UUID campus,
        String prefix,
        String type
    ) {

        UUID id =
            UUID.randomUUID();

        String code =
            suffix();

        jdbcTemplate.update(
            """
            INSERT INTO locations(
                id,
                campus_id,
                name,
                code,
                type,
                is_active
            )
            VALUES (?, ?, ?, ?, ?, TRUE)
            """,
            id,
            campus,
            prefix + " Location " + code,
            prefix + code,
            type
        );

        return id;
    }

    private UUID stockLocation(
        UUID location
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO stock_locations(
                id,
                location_id,
                name,
                type,
                is_active
            )
            VALUES (?, ?, ?, 'STORAGE', TRUE)
            """,
            id,
            location,
            "Waste Storage " + suffix()
        );

        return id;
    }

    private UUID category(
        UUID org
    ) {

        UUID id =
            UUID.randomUUID();

        String code =
            suffix();

        jdbcTemplate.update(
            """
            INSERT INTO categories(
                id,
                organization_id,
                name,
                slug,
                display_order,
                is_active
            )
            VALUES (?, ?, ?, ?, 0, TRUE)
            """,
            id,
            org,
            "Waste Category " + code,
            "waste-category-" + code
        );

        return id;
    }

    private UUID product(
        UUID org,
        UUID category
    ) {

        UUID id =
            UUID.randomUUID();

        String code =
            suffix();

        jdbcTemplate.update(
            """
            INSERT INTO products(
                id,
                organization_id,
                category_id,
                sku,
                name,
                product_type,
                base_price,
                tax_rate,
                track_stock,
                is_prepared,
                is_active
            )
            VALUES (
                ?, ?, ?, ?, ?,
                'PACKAGED',
                10.00,
                0.00,
                TRUE,
                FALSE,
                TRUE
            )
            """,
            id,
            org,
            category,
            "WASTE-" + code,
            "Waste Product " + code
        );

        return id;
    }

    private UUID stockItem(
        UUID org,
        UUID product
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO stock_items(
                id,
                organization_id,
                product_id,
                base_unit,
                low_stock_threshold,
                track_expiry
            )
            VALUES (
                ?, ?, ?,
                'PIECE',
                0.000,
                TRUE
            )
            """,
            id,
            org,
            product
        );

        return id;
    }

    private void balance(
        String physical,
        String reserved
    ) {

        jdbcTemplate.update(
            """
            INSERT INTO stock_balances(
                stock_item_id,
                stock_location_id,
                physical_quantity,
                reserved_quantity
            )
            VALUES (?, ?, ?, ?)
            """,
            stockItemId,
            stockLocationId,
            new BigDecimal(physical),
            new BigDecimal(reserved)
        );
    }

    private UUID lot(
        String quantity,
        OffsetDateTime expiry
    ) {

        UUID id =
            UUID.randomUUID();

        BigDecimal value =
            new BigDecimal(quantity);

        jdbcTemplate.update(
            """
            INSERT INTO stock_lots(
                id,
                stock_item_id,
                stock_location_id,
                lot_number,
                received_at,
                expires_at,
                quantity_received,
                quantity_remaining,
                unit_cost
            )
            VALUES (
                ?, ?, ?, ?,
                CURRENT_TIMESTAMP,
                ?,
                ?, ?,
                2.00
            )
            """,
            id,
            stockItemId,
            stockLocationId,
            "LOT-" + suffix(),
            expiry,
            value,
            value
        );

        return id;
    }

    private void assertBalance(
        String physical,
        String reserved
    ) {

        BigDecimal p =
            jdbcTemplate.queryForObject(
                """
                SELECT physical_quantity
                FROM stock_balances
                WHERE stock_item_id = ?
                  AND stock_location_id = ?
                """,
                BigDecimal.class,
                stockItemId,
                stockLocationId
            );

        BigDecimal r =
            jdbcTemplate.queryForObject(
                """
                SELECT reserved_quantity
                FROM stock_balances
                WHERE stock_item_id = ?
                  AND stock_location_id = ?
                """,
                BigDecimal.class,
                stockItemId,
                stockLocationId
            );

        assertThat(p)
            .isEqualByComparingTo(physical);

        assertThat(r)
            .isEqualByComparingTo(reserved);
    }

    private BigDecimal lotRemaining(
        UUID id
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT quantity_remaining
            FROM stock_lots
            WHERE id = ?
            """,
            BigDecimal.class,
            id
        );
    }

    private Long movementCount(
        UUID waste
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM inventory_movements
            WHERE movement_type = 'WASTE'
              AND reference_type = 'WASTE_RECORD'
              AND reference_id = ?
            """,
            Long.class,
            waste
        );
    }

    private Long count(
        String table,
        UUID id
    ) {

        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE id = ?",
            Long.class,
            id
        );
    }

    private String suffix() {

        return UUID.randomUUID()
            .toString()
            .replace(
                "-",
                ""
            )
            .substring(
                0,
                12
            );
    }
}