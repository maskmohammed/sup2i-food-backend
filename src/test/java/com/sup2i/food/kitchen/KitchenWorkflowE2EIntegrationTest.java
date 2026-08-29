package com.sup2i.food.kitchen;

import com.sup2i.food.kitchen.service.KitchenReadyService;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    properties = {
        "sup2i.security.jwt.secret-base64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=",
        "sup2i.security.mfa.encryption-key-base64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk="
    }
)
@ActiveProfiles("test")
@Testcontainers
class KitchenWorkflowE2EIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
        new PostgreSQLContainer(
            "postgres:17.10-bookworm"
        )
            .withDatabaseName(
                "sup2i_food_test"
            );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KitchenReadyService readyService;

    @Test
    void sequentialKitchensPromoteOrderOnlyWhenLastTicketIsReady() {

        WorkflowFixture fixture =
            insertPreparingWorkflow(
                "SEQ"
            );

        OffsetDateTime firstReadyAt =
            OffsetDateTime.parse(
                "2026-08-29T13:00:00+01:00"
            );

        OffsetDateTime secondReadyAt =
            OffsetDateTime.parse(
                "2026-08-29T13:00:01+01:00"
            );

        assertInitialWorkflow(
            fixture
        );

        readyService.markReady(
            fixture.actorId(),
            fixture.ticketAId(),
            firstReadyAt
        );

        assertThat(
            orderStatus(
                fixture.orderId()
            )
        ).isEqualTo(
            "PREPARING"
        );

        assertThat(
            ticketStatus(
                fixture.ticketAId()
            )
        ).isEqualTo(
            "READY"
        );

        assertThat(
            ticketStatus(
                fixture.ticketBId()
            )
        ).isEqualTo(
            "PREPARING"
        );

        assertThat(
            ticketItemStatus(
                fixture.ticketItemAId()
            )
        ).isEqualTo(
            "READY"
        );

        assertThat(
            ticketItemStatus(
                fixture.ticketItemBId()
            )
        ).isEqualTo(
            "PREPARING"
        );

        assertThat(
            orderReadyAt(
                fixture.orderId()
            )
        ).isNull();

        assertInstantEquals(
            ticketReadyAt(
                fixture.ticketAId()
            ),
            firstReadyAt
        );

        assertInstantEquals(
            ticketItemReadyAt(
                fixture.ticketItemAId()
            ),
            firstReadyAt
        );

        assertThat(
            readyHistoryCount(
                fixture.orderId()
            )
        ).isZero();

        assertStockUntouched(
            fixture.orderId()
        );

        readyService.markReady(
            fixture.actorId(),
            fixture.ticketBId(),
            secondReadyAt
        );

        assertFinalReadyWorkflow(
            fixture
        );

        assertInstantEquals(
            orderReadyAt(
                fixture.orderId()
            ),
            secondReadyAt
        );

        assertInstantEquals(
            ticketReadyAt(
                fixture.ticketAId()
            ),
            firstReadyAt
        );

        assertInstantEquals(
            ticketReadyAt(
                fixture.ticketBId()
            ),
            secondReadyAt
        );

        assertInstantEquals(
            ticketItemReadyAt(
                fixture.ticketItemAId()
            ),
            firstReadyAt
        );

        assertInstantEquals(
            ticketItemReadyAt(
                fixture.ticketItemBId()
            ),
            secondReadyAt
        );

        assertReadyHistory(
            fixture
        );

        assertThat(
            orderVersion(
                fixture.orderId()
            )
        ).isEqualTo(
            1
        );

        assertStockUntouched(
            fixture.orderId()
        );
    }

    @Test
    void concurrentLastReadyRaceSerializesAndWritesOneGlobalTransition()
        throws Exception {

        WorkflowFixture fixture =
            insertPreparingWorkflow(
                "CON"
            );

        OffsetDateTime firstReadyAt =
            OffsetDateTime.parse(
                "2026-08-29T14:00:00+01:00"
            );

        OffsetDateTime secondReadyAt =
            OffsetDateTime.parse(
                "2026-08-29T14:00:01+01:00"
            );

        CountDownLatch ready =
            new CountDownLatch(
                2
            );

        CountDownLatch start =
            new CountDownLatch(
                1
            );

        ExecutorService executor =
            Executors.newFixedThreadPool(
                2
            );

        try {
            Future<Void> first =
                executor.submit(() -> {

                    ready.countDown();

                    boolean released =
                        start.await(
                            10,
                            TimeUnit.SECONDS
                        );

                    if (!released) {
                        throw new IllegalStateException(
                            "Concurrent kitchen start latch timed out."
                        );
                    }

                    readyService.markReady(
                        fixture.actorId(),
                        fixture.ticketAId(),
                        firstReadyAt
                    );

                    return null;
                });

            Future<Void> second =
                executor.submit(() -> {

                    ready.countDown();

                    boolean released =
                        start.await(
                            10,
                            TimeUnit.SECONDS
                        );

                    if (!released) {
                        throw new IllegalStateException(
                            "Concurrent kitchen start latch timed out."
                        );
                    }

                    readyService.markReady(
                        fixture.actorId(),
                        fixture.ticketBId(),
                        secondReadyAt
                    );

                    return null;
                });

            assertThat(
                ready.await(
                    10,
                    TimeUnit.SECONDS
                )
            ).isTrue();

            start.countDown();

            first.get(
                30,
                TimeUnit.SECONDS
            );

            second.get(
                30,
                TimeUnit.SECONDS
            );
        }
        finally {
            start.countDown();

            executor.shutdownNow();

            assertThat(
                executor.awaitTermination(
                    10,
                    TimeUnit.SECONDS
                )
            ).isTrue();
        }

        assertFinalReadyWorkflow(
            fixture
        );

        assertInstantEquals(
            ticketReadyAt(
                fixture.ticketAId()
            ),
            firstReadyAt
        );

        assertInstantEquals(
            ticketReadyAt(
                fixture.ticketBId()
            ),
            secondReadyAt
        );

        assertInstantEquals(
            ticketItemReadyAt(
                fixture.ticketItemAId()
            ),
            firstReadyAt
        );

        assertInstantEquals(
            ticketItemReadyAt(
                fixture.ticketItemBId()
            ),
            secondReadyAt
        );

        OffsetDateTime persistedOrderReadyAt =
            orderReadyAt(
                fixture.orderId()
            );

        assertThat(
            persistedOrderReadyAt
        ).isNotNull();

        boolean matchesFirst =
            persistedOrderReadyAt
                .toInstant()
                .equals(
                    firstReadyAt.toInstant()
                );

        boolean matchesSecond =
            persistedOrderReadyAt
                .toInstant()
                .equals(
                    secondReadyAt.toInstant()
                );

        assertThat(
            matchesFirst || matchesSecond
        ).isTrue();

        assertReadyHistory(
            fixture
        );

        assertThat(
            orderVersion(
                fixture.orderId()
            )
        ).isEqualTo(
            1
        );

        assertStockUntouched(
            fixture.orderId()
        );
    }

    @Test
    void persistedFinalReadyReplayIsPureNoOp() {

        WorkflowFixture fixture =
            insertPreparingWorkflow(
                "RPL"
            );

        OffsetDateTime firstReadyAt =
            OffsetDateTime.parse(
                "2026-08-29T15:00:00+01:00"
            );

        OffsetDateTime secondReadyAt =
            OffsetDateTime.parse(
                "2026-08-29T15:00:01+01:00"
            );

        OffsetDateTime replayAt =
            OffsetDateTime.parse(
                "2026-08-29T16:00:00+01:00"
            );

        readyService.markReady(
            fixture.actorId(),
            fixture.ticketAId(),
            firstReadyAt
        );

        readyService.markReady(
            fixture.actorId(),
            fixture.ticketBId(),
            secondReadyAt
        );

        OffsetDateTime orderReadyBefore =
            orderReadyAt(
                fixture.orderId()
            );

        OffsetDateTime ticketReadyBefore =
            ticketReadyAt(
                fixture.ticketAId()
            );

        OffsetDateTime itemReadyBefore =
            ticketItemReadyAt(
                fixture.ticketItemAId()
            );

        int versionBefore =
            orderVersion(
                fixture.orderId()
            );

        long historyBefore =
            readyHistoryCount(
                fixture.orderId()
            );

        readyService.markReady(
            fixture.actorId(),
            fixture.ticketAId(),
            replayAt
        );

        assertFinalReadyWorkflow(
            fixture
        );

        assertInstantEquals(
            orderReadyAt(
                fixture.orderId()
            ),
            orderReadyBefore
        );

        assertInstantEquals(
            ticketReadyAt(
                fixture.ticketAId()
            ),
            ticketReadyBefore
        );

        assertInstantEquals(
            ticketItemReadyAt(
                fixture.ticketItemAId()
            ),
            itemReadyBefore
        );

        assertThat(
            orderVersion(
                fixture.orderId()
            )
        ).isEqualTo(
            versionBefore
        );

        assertThat(
            readyHistoryCount(
                fixture.orderId()
            )
        ).isEqualTo(
            historyBefore
        );

        assertThat(
            historyBefore
        ).isEqualTo(
            1L
        );

        assertStockUntouched(
            fixture.orderId()
        );
    }

    private void assertInitialWorkflow(
        WorkflowFixture fixture
    ) {

        assertThat(
            orderStatus(
                fixture.orderId()
            )
        ).isEqualTo(
            "PREPARING"
        );

        assertThat(
            ticketStatus(
                fixture.ticketAId()
            )
        ).isEqualTo(
            "PREPARING"
        );

        assertThat(
            ticketStatus(
                fixture.ticketBId()
            )
        ).isEqualTo(
            "PREPARING"
        );

        assertThat(
            ticketItemStatus(
                fixture.ticketItemAId()
            )
        ).isEqualTo(
            "PREPARING"
        );

        assertThat(
            ticketItemStatus(
                fixture.ticketItemBId()
            )
        ).isEqualTo(
            "PREPARING"
        );

        assertThat(
            orderReadyAt(
                fixture.orderId()
            )
        ).isNull();

        assertThat(
            readyHistoryCount(
                fixture.orderId()
            )
        ).isZero();

        assertThat(
            orderVersion(
                fixture.orderId()
            )
        ).isZero();

        assertStockUntouched(
            fixture.orderId()
        );
    }

    private void assertFinalReadyWorkflow(
        WorkflowFixture fixture
    ) {

        assertThat(
            orderStatus(
                fixture.orderId()
            )
        ).isEqualTo(
            "READY"
        );

        assertThat(
            ticketStatus(
                fixture.ticketAId()
            )
        ).isEqualTo(
            "READY"
        );

        assertThat(
            ticketStatus(
                fixture.ticketBId()
            )
        ).isEqualTo(
            "READY"
        );

        assertThat(
            ticketItemStatus(
                fixture.ticketItemAId()
            )
        ).isEqualTo(
            "READY"
        );

        assertThat(
            ticketItemStatus(
                fixture.ticketItemBId()
            )
        ).isEqualTo(
            "READY"
        );

        assertThat(
            orderReadyAt(
                fixture.orderId()
            )
        ).isNotNull();

        assertThat(
            readyHistoryCount(
                fixture.orderId()
            )
        ).isEqualTo(
            1L
        );
    }

    private void assertReadyHistory(
        WorkflowFixture fixture
    ) {

        assertThat(
            readyHistoryCount(
                fixture.orderId()
            )
        ).isEqualTo(
            1L
        );

        assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT source
                FROM order_status_history
                WHERE order_id = ?
                  AND from_status = 'PREPARING'
                  AND to_status = 'READY'
                """,
                String.class,
                fixture.orderId()
            )
        ).isEqualTo(
            "API"
        );

        assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT changed_by
                FROM order_status_history
                WHERE order_id = ?
                  AND from_status = 'PREPARING'
                  AND to_status = 'READY'
                """,
                UUID.class,
                fixture.orderId()
            )
        ).isEqualTo(
            fixture.actorId()
        );

        assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT reason
                FROM order_status_history
                WHERE order_id = ?
                  AND from_status = 'PREPARING'
                  AND to_status = 'READY'
                """,
                String.class,
                fixture.orderId()
            )
        ).isEqualTo(
            "Order ready for collection."
        );
    }

    private void assertStockUntouched(
        UUID orderId
    ) {

        assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM stock_reservations
                WHERE order_id = ?
                """,
                Long.class,
                orderId
            )
        ).isZero();

        assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM inventory_movements
                WHERE reference_type = 'ORDER'
                  AND reference_id = ?
                """,
                Long.class,
                orderId
            )
        ).isZero();
    }

    private WorkflowFixture insertPreparingWorkflow(
        String prefix
    ) {

        String suffix =
            randomSuffix();

        UUID organizationId =
            UUID.randomUUID();

        UUID campusId =
            UUID.randomUUID();

        UUID sourceLocationId =
            UUID.randomUUID();

        UUID kitchenAId =
            UUID.randomUUID();

        UUID kitchenBId =
            UUID.randomUUID();

        UUID actorId =
            UUID.randomUUID();

        UUID categoryId =
            UUID.randomUUID();

        UUID productId =
            UUID.randomUUID();

        UUID orderId =
            UUID.randomUUID();

        UUID orderItemAId =
            UUID.randomUUID();

        UUID orderItemBId =
            UUID.randomUUID();

        UUID ticketAId =
            UUID.randomUUID();

        UUID ticketBId =
            UUID.randomUUID();

        UUID ticketItemAId =
            UUID.randomUUID();

        UUID ticketItemBId =
            UUID.randomUUID();

        OffsetDateTime startedAt =
            OffsetDateTime.parse(
                "2026-08-29T12:30:00+01:00"
            );

        jdbcTemplate.update(
            """
            INSERT INTO organizations (
                id,
                name,
                code,
                is_active
            )
            VALUES (?, ?, ?, TRUE)
            """,
            organizationId,
            prefix + " Kitchen Organization",
            prefix + "-ORG-" + suffix
        );

        jdbcTemplate.update(
            """
            INSERT INTO campuses (
                id,
                organization_id,
                name,
                code,
                is_active
            )
            VALUES (?, ?, ?, ?, TRUE)
            """,
            campusId,
            organizationId,
            prefix + " Kitchen Campus",
            prefix + "-C-" + suffix
        );

        jdbcTemplate.update(
            """
            INSERT INTO locations (
                id,
                campus_id,
                name,
                code,
                type,
                is_active
            )
            VALUES (?, ?, ?, ?, 'SNACK', TRUE)
            """,
            sourceLocationId,
            campusId,
            prefix + " Source",
            prefix + "-S-" + suffix
        );

        jdbcTemplate.update(
            """
            INSERT INTO locations (
                id,
                campus_id,
                name,
                code,
                type,
                is_active
            )
            VALUES (?, ?, ?, ?, 'KITCHEN', TRUE)
            """,
            kitchenAId,
            campusId,
            prefix + " Kitchen A",
            prefix + "-KA-" + suffix
        );

        jdbcTemplate.update(
            """
            INSERT INTO locations (
                id,
                campus_id,
                name,
                code,
                type,
                is_active
            )
            VALUES (?, ?, ?, ?, 'KITCHEN', TRUE)
            """,
            kitchenBId,
            campusId,
            prefix + " Kitchen B",
            prefix + "-KB-" + suffix
        );

        jdbcTemplate.update(
            """
            INSERT INTO users (
                id,
                organization_id,
                email,
                first_name,
                last_name,
                status
            )
            VALUES (?, ?, ?, ?, ?, 'ACTIVE')
            """,
            actorId,
            organizationId,
            "kitchen-"
                + prefix.toLowerCase()
                + "-"
                + suffix.toLowerCase()
                + "@sup2i.test",
            "Kitchen",
            prefix
        );

        jdbcTemplate.update(
            """
            INSERT INTO categories (
                id,
                organization_id,
                name,
                slug,
                display_order,
                is_active
            )
            VALUES (?, ?, ?, ?, 0, TRUE)
            """,
            categoryId,
            organizationId,
            prefix + " Category",
            "kitchen-"
                + prefix.toLowerCase()
                + "-"
                + suffix.toLowerCase()
        );

        jdbcTemplate.update(
            """
            INSERT INTO products (
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
                'SERVICE',
                ?, 0,
                FALSE,
                FALSE,
                TRUE
            )
            """,
            productId,
            organizationId,
            categoryId,
            prefix + "-SKU-" + suffix,
            prefix + " Kitchen Product",
            new BigDecimal(
                "5.00"
            )
        );

        jdbcTemplate.update(
            """
            INSERT INTO orders (
                id,
                organization_id,
                campus_id,
                location_id,
                order_number,
                business_date,
                source,
                status,
                subtotal,
                discount_total,
                total,
                currency,
                paid_at,
                order_type,
                payment_status,
                tax_total,
                version
            )
            VALUES (
                ?, ?, ?, ?,
                ?, ?,
                'API',
                'PREPARING',
                ?, 0, ?,
                'MAD',
                ?,
                'MOBILE_SNACK',
                'COMPLETED',
                0,
                0
            )
            """,
            orderId,
            organizationId,
            campusId,
            sourceLocationId,
            "K-" + prefix + "-" + suffix,
            LocalDate.of(
                2026,
                8,
                29
            ),
            new BigDecimal(
                "10.00"
            ),
            new BigDecimal(
                "10.00"
            ),
            startedAt.minusMinutes(
                10
            )
        );

        jdbcTemplate.update(
            """
            INSERT INTO order_items (
                id,
                order_id,
                product_id,
                product_name_snapshot,
                unit_price,
                quantity,
                discount_amount,
                line_total,
                sku_snapshot,
                tax_rate_snapshot,
                line_tax
            )
            VALUES (
                ?, ?, ?, ?,
                ?, 1, 0, ?,
                ?, 0, 0
            )
            """,
            orderItemAId,
            orderId,
            productId,
            prefix + " Product A",
            new BigDecimal(
                "5.00"
            ),
            new BigDecimal(
                "5.00"
            ),
            prefix + "-A-" + suffix
        );

        jdbcTemplate.update(
            """
            INSERT INTO order_items (
                id,
                order_id,
                product_id,
                product_name_snapshot,
                unit_price,
                quantity,
                discount_amount,
                line_total,
                sku_snapshot,
                tax_rate_snapshot,
                line_tax
            )
            VALUES (
                ?, ?, ?, ?,
                ?, 1, 0, ?,
                ?, 0, 0
            )
            """,
            orderItemBId,
            orderId,
            productId,
            prefix + " Product B",
            new BigDecimal(
                "5.00"
            ),
            new BigDecimal(
                "5.00"
            ),
            prefix + "-B-" + suffix
        );

        jdbcTemplate.update(
            """
            INSERT INTO kitchen_tickets (
                id,
                order_id,
                kitchen_location_id,
                status,
                priority,
                queued_at,
                started_at,
                assigned_to
            )
            VALUES (
                ?, ?, ?,
                'PREPARING',
                0,
                ?,
                ?,
                ?
            )
            """,
            ticketAId,
            orderId,
            kitchenAId,
            startedAt.minusMinutes(
                2
            ),
            startedAt,
            actorId
        );

        jdbcTemplate.update(
            """
            INSERT INTO kitchen_tickets (
                id,
                order_id,
                kitchen_location_id,
                status,
                priority,
                queued_at,
                started_at,
                assigned_to
            )
            VALUES (
                ?, ?, ?,
                'PREPARING',
                0,
                ?,
                ?,
                ?
            )
            """,
            ticketBId,
            orderId,
            kitchenBId,
            startedAt.minusMinutes(
                2
            ),
            startedAt,
            actorId
        );

        jdbcTemplate.update(
            """
            INSERT INTO kitchen_ticket_items (
                id,
                kitchen_ticket_id,
                order_item_id,
                menu_selection_id,
                quantity,
                status,
                started_at
            )
            VALUES (
                ?, ?, ?,
                NULL,
                1.000,
                'PREPARING',
                ?
            )
            """,
            ticketItemAId,
            ticketAId,
            orderItemAId,
            startedAt
        );

        jdbcTemplate.update(
            """
            INSERT INTO kitchen_ticket_items (
                id,
                kitchen_ticket_id,
                order_item_id,
                menu_selection_id,
                quantity,
                status,
                started_at
            )
            VALUES (
                ?, ?, ?,
                NULL,
                1.000,
                'PREPARING',
                ?
            )
            """,
            ticketItemBId,
            ticketBId,
            orderItemBId,
            startedAt
        );

        return new WorkflowFixture(
            organizationId,
            actorId,
            orderId,
            ticketAId,
            ticketBId,
            ticketItemAId,
            ticketItemBId
        );
    }

    private String orderStatus(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT status
            FROM orders
            WHERE id = ?
            """,
            String.class,
            orderId
        );
    }

    private int orderVersion(
        UUID orderId
    ) {

        Integer result =
            jdbcTemplate.queryForObject(
                """
                SELECT version
                FROM orders
                WHERE id = ?
                """,
                Integer.class,
                orderId
            );

        if (result == null) {
            throw new IllegalStateException(
                "Order version is null."
            );
        }

        return result;
    }

    private OffsetDateTime orderReadyAt(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT ready_at
            FROM orders
            WHERE id = ?
            """,
            (resultSet, rowNumber) ->
                resultSet.getObject(
                    1,
                    OffsetDateTime.class
                ),
            orderId
        );
    }

    private String ticketStatus(
        UUID ticketId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT status
            FROM kitchen_tickets
            WHERE id = ?
            """,
            String.class,
            ticketId
        );
    }

    private OffsetDateTime ticketReadyAt(
        UUID ticketId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT ready_at
            FROM kitchen_tickets
            WHERE id = ?
            """,
            (resultSet, rowNumber) ->
                resultSet.getObject(
                    1,
                    OffsetDateTime.class
                ),
            ticketId
        );
    }

    private String ticketItemStatus(
        UUID ticketItemId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT status
            FROM kitchen_ticket_items
            WHERE id = ?
            """,
            String.class,
            ticketItemId
        );
    }

    private OffsetDateTime ticketItemReadyAt(
        UUID ticketItemId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT ready_at
            FROM kitchen_ticket_items
            WHERE id = ?
            """,
            (resultSet, rowNumber) ->
                resultSet.getObject(
                    1,
                    OffsetDateTime.class
                ),
            ticketItemId
        );
    }

    private long readyHistoryCount(
        UUID orderId
    ) {

        Long result =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM order_status_history
                WHERE order_id = ?
                  AND from_status = 'PREPARING'
                  AND to_status = 'READY'
                """,
                Long.class,
                orderId
            );

        if (result == null) {
            throw new IllegalStateException(
                "Ready history count is null."
            );
        }

        return result;
    }

    private void assertInstantEquals(
        OffsetDateTime actual,
        OffsetDateTime expected
    ) {

        assertThat(
            actual
        ).isNotNull();

        assertThat(
            actual.toInstant()
        ).isEqualTo(
            expected.toInstant()
        );
    }

    private String randomSuffix() {

        return UUID.randomUUID()
            .toString()
            .replace(
                "-",
                ""
            )
            .substring(
                0,
                8
            );
    }

    private record WorkflowFixture(
        UUID organizationId,
        UUID actorId,
        UUID orderId,
        UUID ticketAId,
        UUID ticketBId,
        UUID ticketItemAId,
        UUID ticketItemBId
    ) {
    }
}
