package com.sup2i.food.payment;

import com.sup2i.food.kitchen.service.KitchenReadyService;
import com.sup2i.food.kitchen.service.KitchenStartService;
import com.sup2i.food.payment.domain.PaymentMethod;
import com.sup2i.food.payment.domain.PaymentStatus;
import com.sup2i.food.payment.service.PaymentCaptureCommand;
import com.sup2i.food.payment.service.PaymentCaptureResult;
import com.sup2i.food.payment.service.PaymentService;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
    properties = {
        "sup2i.security.jwt.secret-base64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=",
        "sup2i.security.mfa.encryption-key-base64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk="
    }
)
@ActiveProfiles("test")
@Testcontainers
class PaymentKitchenIntegrationE2ETest {

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
    private PaymentService paymentService;

    @Autowired
    private KitchenStartService kitchenStartService;

    @Autowired
    private KitchenReadyService kitchenReadyService;

    @Test
    void freshPaymentAtomicallyQueuesOrderForKitchen() {

        WorkflowFixture fixture =
            insertAwaitingPaymentWorkflow(
                "FRESH",
                true
            );

        PaymentCaptureCommand command =
            cashCommand(
                "FRESH",
                fixture.amount()
            );

        PaymentCaptureResult result =
            paymentService.capture(
                fixture.actorId(),
                fixture.orderId(),
                command
            );

        assertThat(
            result.replayed()
        ).isFalse();

        assertThat(
            result.status()
        ).isEqualTo(
            PaymentStatus.COMPLETED
        );

        assertThat(
            result.orderId()
        ).isEqualTo(
            fixture.orderId()
        );

        assertThat(
            orderStatus(
                fixture.orderId()
            )
        ).isEqualTo(
            "QUEUED"
        );

        assertThat(
            orderPaymentStatus(
                fixture.orderId()
            )
        ).isEqualTo(
            "COMPLETED"
        );

        assertThat(
            orderPaidAt(
                fixture.orderId()
            )
        ).isNotNull();

        assertThat(
            paymentCount(
                fixture.orderId()
            )
        ).isEqualTo(
            1L
        );

        assertThat(
            captureCompletedEventCount(
                fixture.orderId()
            )
        ).isEqualTo(
            1L
        );

        assertThat(
            kitchenTicketCount(
                fixture.orderId()
            )
        ).isEqualTo(
            1L
        );

        assertThat(
            kitchenTicketItemCount(
                fixture.orderId()
            )
        ).isEqualTo(
            1L
        );

        UUID ticketId =
            kitchenTicketId(
                fixture.orderId()
            );

        assertThat(
            kitchenTicketStatus(
                ticketId
            )
        ).isEqualTo(
            "QUEUED"
        );

        assertThat(
            kitchenTicketItemStatus(
                ticketId
            )
        ).isEqualTo(
            "QUEUED"
        );

        assertThat(
            transitionCount(
                fixture.orderId(),
                "AWAITING_PAYMENT",
                "PAID"
            )
        ).isEqualTo(
            1L
        );

        assertThat(
            transitionCount(
                fixture.orderId(),
                "PAID",
                "QUEUED"
            )
        ).isEqualTo(
            1L
        );

        assertThat(
            orderReferencedMovementCount(
                fixture.orderId()
            )
        ).isZero();
    }

    @Test
    void samePaymentKeyReplaysAfterQueuedWithoutRequeueing() {

        WorkflowFixture fixture =
            insertAwaitingPaymentWorkflow(
                "QUEUED-RPL",
                true
            );

        PaymentCaptureCommand command =
            cashCommand(
                "QUEUED-RPL",
                fixture.amount()
            );

        PaymentCaptureResult first =
            paymentService.capture(
                fixture.actorId(),
                fixture.orderId(),
                command
            );

        UUID ticketId =
            kitchenTicketId(
                fixture.orderId()
            );

        PaymentCaptureResult replay =
            paymentService.capture(
                fixture.actorId(),
                fixture.orderId(),
                command
            );

        assertThat(
            replay.replayed()
        ).isTrue();

        assertThat(
            replay.paymentId()
        ).isEqualTo(
            first.paymentId()
        );

        assertThat(
            orderStatus(
                fixture.orderId()
            )
        ).isEqualTo(
            "QUEUED"
        );

        assertThat(
            kitchenTicketStatus(
                ticketId
            )
        ).isEqualTo(
            "QUEUED"
        );

        assertThat(
            paymentCount(
                fixture.orderId()
            )
        ).isEqualTo(
            1L
        );

        assertThat(
            captureCompletedEventCount(
                fixture.orderId()
            )
        ).isEqualTo(
            1L
        );

        assertThat(
            kitchenTicketCount(
                fixture.orderId()
            )
        ).isEqualTo(
            1L
        );

        assertThat(
            kitchenTicketItemCount(
                fixture.orderId()
            )
        ).isEqualTo(
            1L
        );

        assertThat(
            transitionCount(
                fixture.orderId(),
                "AWAITING_PAYMENT",
                "PAID"
            )
        ).isEqualTo(
            1L
        );

        assertThat(
            transitionCount(
                fixture.orderId(),
                "PAID",
                "QUEUED"
            )
        ).isEqualTo(
            1L
        );
    }

    @Test
    void paymentReplaySurvivesPreparingAndReadyProgression() {

        WorkflowFixture fixture =
            insertAwaitingPaymentWorkflow(
                "DOWNSTREAM",
                true
            );

        PaymentCaptureCommand command =
            cashCommand(
                "DOWNSTREAM",
                fixture.amount()
            );

        PaymentCaptureResult first =
            paymentService.capture(
                fixture.actorId(),
                fixture.orderId(),
                command
            );

        UUID ticketId =
            kitchenTicketId(
                fixture.orderId()
            );

        OffsetDateTime startedAt =
            OffsetDateTime.now()
                .plusSeconds(
                    1
                );

        kitchenStartService.startTicket(
            fixture.actorId(),
            ticketId,
            startedAt
        );

        assertThat(
            orderStatus(
                fixture.orderId()
            )
        ).isEqualTo(
            "PREPARING"
        );

        PaymentCaptureResult preparingReplay =
            paymentService.capture(
                fixture.actorId(),
                fixture.orderId(),
                command
            );

        assertThat(
            preparingReplay.replayed()
        ).isTrue();

        assertThat(
            preparingReplay.paymentId()
        ).isEqualTo(
            first.paymentId()
        );

        assertThat(
            orderStatus(
                fixture.orderId()
            )
        ).isEqualTo(
            "PREPARING"
        );

        OffsetDateTime readyAt =
            startedAt.plusSeconds(
                1
            );

        kitchenReadyService.markReady(
            fixture.actorId(),
            ticketId,
            readyAt
        );

        assertThat(
            orderStatus(
                fixture.orderId()
            )
        ).isEqualTo(
            "READY"
        );

        PaymentCaptureResult readyReplay =
            paymentService.capture(
                fixture.actorId(),
                fixture.orderId(),
                command
            );

        assertThat(
            readyReplay.replayed()
        ).isTrue();

        assertThat(
            readyReplay.paymentId()
        ).isEqualTo(
            first.paymentId()
        );

        assertThat(
            orderStatus(
                fixture.orderId()
            )
        ).isEqualTo(
            "READY"
        );

        assertThat(
            orderPaymentStatus(
                fixture.orderId()
            )
        ).isEqualTo(
            "COMPLETED"
        );

        assertThat(
            paymentCount(
                fixture.orderId()
            )
        ).isEqualTo(
            1L
        );

        assertThat(
            captureCompletedEventCount(
                fixture.orderId()
            )
        ).isEqualTo(
            1L
        );

        assertThat(
            kitchenTicketCount(
                fixture.orderId()
            )
        ).isEqualTo(
            1L
        );

        assertThat(
            transitionCount(
                fixture.orderId(),
                "AWAITING_PAYMENT",
                "PAID"
            )
        ).isEqualTo(
            1L
        );

        assertThat(
            transitionCount(
                fixture.orderId(),
                "PAID",
                "QUEUED"
            )
        ).isEqualTo(
            1L
        );

        assertThat(
            transitionCount(
                fixture.orderId(),
                "QUEUED",
                "PREPARING"
            )
        ).isEqualTo(
            1L
        );

        assertThat(
            transitionCount(
                fixture.orderId(),
                "PREPARING",
                "READY"
            )
        ).isEqualTo(
            1L
        );

        assertThat(
            orderReferencedMovementCount(
                fixture.orderId()
            )
        ).isZero();
    }

    @Test
    void kitchenRoutingFailureRollsBackEntirePaymentCapture() {

        WorkflowFixture fixture =
            insertAwaitingPaymentWorkflow(
                "ROLLBACK",
                false
            );

        PaymentCaptureCommand command =
            cashCommand(
                "ROLLBACK",
                fixture.amount()
            );

        assertThatThrownBy(() ->
            paymentService.capture(
                fixture.actorId(),
                fixture.orderId(),
                command
            )
        )
            .isInstanceOf(
                RuntimeException.class
            );

        assertThat(
            orderStatus(
                fixture.orderId()
            )
        ).isEqualTo(
            "AWAITING_PAYMENT"
        );

        assertThat(
            orderPaymentStatus(
                fixture.orderId()
            )
        ).isEqualTo(
            "PENDING"
        );

        assertThat(
            orderPaidAt(
                fixture.orderId()
            )
        ).isNull();

        assertThat(
            paymentCount(
                fixture.orderId()
            )
        ).isZero();

        assertThat(
            captureCompletedEventCount(
                fixture.orderId()
            )
        ).isZero();

        assertThat(
            kitchenTicketCount(
                fixture.orderId()
            )
        ).isZero();

        assertThat(
            kitchenTicketItemCount(
                fixture.orderId()
            )
        ).isZero();

        assertThat(
            transitionCount(
                fixture.orderId(),
                "AWAITING_PAYMENT",
                "PAID"
            )
        ).isZero();

        assertThat(
            transitionCount(
                fixture.orderId(),
                "PAID",
                "QUEUED"
            )
        ).isZero();

        assertThat(
            orderReferencedMovementCount(
                fixture.orderId()
            )
        ).isZero();
    }

    private WorkflowFixture insertAwaitingPaymentWorkflow(
        String prefix,
        boolean withRoute
    ) {

        String suffix =
            randomSuffix();

        UUID organizationId =
            UUID.randomUUID();

        UUID campusId =
            UUID.randomUUID();

        UUID sourceLocationId =
            UUID.randomUUID();

        UUID kitchenLocationId =
            UUID.randomUUID();

        UUID actorId =
            UUID.randomUUID();

        UUID categoryId =
            UUID.randomUUID();

        UUID productId =
            UUID.randomUUID();

        UUID orderId =
            UUID.randomUUID();

        UUID orderItemId =
            UUID.randomUUID();

        BigDecimal amount =
            new BigDecimal(
                "10.00"
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
            prefix + " Organization",
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
            prefix + " Campus",
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
            kitchenLocationId,
            campusId,
            prefix + " Kitchen",
            prefix + "-K-" + suffix
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
            "payment-kitchen-"
                + prefix.toLowerCase()
                + "-"
                + suffix.toLowerCase()
                + "@sup2i.test",
            "Payment",
            "Kitchen"
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
            "payment-kitchen-"
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
                'PREPARED',
                ?, 0,
                FALSE,
                TRUE,
                TRUE
            )
            """,
            productId,
            organizationId,
            categoryId,
            prefix + "-SKU-" + suffix,
            prefix + " Prepared Product",
            amount
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
                payment_expires_at
            )
            VALUES (
                ?, ?, ?, ?,
                ?, ?,
                'MOBILE',
                'AWAITING_PAYMENT',
                ?, 0, ?,
                'MAD',
                ?
            )
            """,
            orderId,
            organizationId,
            campusId,
            sourceLocationId,
            "PK-" + prefix + "-" + suffix,
            LocalDate.now(),
            amount,
            amount,
            OffsetDateTime.now()
                .plusMinutes(
                    30
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
                line_total
            )
            VALUES (
                ?, ?, ?, ?,
                ?, 1, 0, ?
            )
            """,
            orderItemId,
            orderId,
            productId,
            prefix + " Prepared Product",
            amount,
            amount
        );

        if (withRoute) {

            jdbcTemplate.update(
                """
                INSERT INTO preparation_routes (
                    id,
                    source_location_id,
                    kitchen_location_id,
                    category_id,
                    product_id,
                    variant_id,
                    priority,
                    is_active
                )
                VALUES (
                    ?, ?, ?,
                    NULL,
                    NULL,
                    NULL,
                    100,
                    TRUE
                )
                """,
                UUID.randomUUID(),
                sourceLocationId,
                kitchenLocationId
            );
        }

        return new WorkflowFixture(
            organizationId,
            actorId,
            orderId,
            amount
        );
    }

    private PaymentCaptureCommand cashCommand(
        String prefix,
        BigDecimal amount
    ) {

        return new PaymentCaptureCommand(
            PaymentMethod.CASH,
            "payment-kitchen-"
                + prefix.toLowerCase()
                + "-"
                + randomSuffix(),
            amount,
            null,
            null
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

    private String orderPaymentStatus(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT payment_status
            FROM orders
            WHERE id = ?
            """,
            String.class,
            orderId
        );
    }

    private OffsetDateTime orderPaidAt(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT paid_at
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

    private long paymentCount(
        UUID orderId
    ) {

        return count(
            """
            SELECT COUNT(*)
            FROM payments
            WHERE order_id = ?
            """,
            orderId
        );
    }

    private long captureCompletedEventCount(
        UUID orderId
    ) {

        return count(
            """
            SELECT COUNT(*)
            FROM payment_events pe
            JOIN payments p
              ON p.id = pe.payment_id
            WHERE p.order_id = ?
              AND pe.event_type = 'CAPTURE_COMPLETED'
            """,
            orderId
        );
    }

    private long kitchenTicketCount(
        UUID orderId
    ) {

        return count(
            """
            SELECT COUNT(*)
            FROM kitchen_tickets
            WHERE order_id = ?
            """,
            orderId
        );
    }

    private long kitchenTicketItemCount(
        UUID orderId
    ) {

        return count(
            """
            SELECT COUNT(*)
            FROM kitchen_ticket_items kti
            JOIN kitchen_tickets kt
              ON kt.id = kti.kitchen_ticket_id
            WHERE kt.order_id = ?
            """,
            orderId
        );
    }

    private UUID kitchenTicketId(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT id
            FROM kitchen_tickets
            WHERE order_id = ?
            """,
            UUID.class,
            orderId
        );
    }

    private String kitchenTicketStatus(
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

    private String kitchenTicketItemStatus(
        UUID ticketId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT status
            FROM kitchen_ticket_items
            WHERE kitchen_ticket_id = ?
            """,
            String.class,
            ticketId
        );
    }

    private long transitionCount(
        UUID orderId,
        String fromStatus,
        String toStatus
    ) {

        Long result =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM order_status_history
                WHERE order_id = ?
                  AND from_status = ?
                  AND to_status = ?
                """,
                Long.class,
                orderId,
                fromStatus,
                toStatus
            );

        if (result == null) {
            throw new IllegalStateException(
                "Order transition count is null."
            );
        }

        return result;
    }

    private long orderReferencedMovementCount(
        UUID orderId
    ) {

        return count(
            """
            SELECT COUNT(*)
            FROM inventory_movements
            WHERE reference_type = 'ORDER'
              AND reference_id = ?
            """,
            orderId
        );
    }

    private long count(
        String sql,
        UUID id
    ) {

        Long result =
            jdbcTemplate.queryForObject(
                sql,
                Long.class,
                id
            );

        if (result == null) {
            throw new IllegalStateException(
                "Database count is null."
            );
        }

        return result;
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
        BigDecimal amount
    ) {
    }
}
