package com.sup2i.food.pos.service;

import com.sup2i.food.payment.domain.PaymentMethod;
import com.sup2i.food.payment.service.PaymentCaptureCommand;
import com.sup2i.food.payment.service.PaymentCaptureResult;
import com.sup2i.food.payment.service.PaymentService;
import com.sup2i.food.pos.api.dto.PosCheckoutResponse;
import com.sup2i.food.pos.api.dto.PosPaymentMethodRequest;
import com.sup2i.food.pos.api.dto.PosPaymentRequest;
import com.sup2i.food.pos.api.dto.PosReceiptResponse;
import com.sup2i.food.pos.exception.PosErrorCode;
import com.sup2i.food.pos.exception.PosException;
import com.sup2i.food.pos.exception.PosValidationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class PosCheckoutService {

    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper;
    private final PaymentService paymentService;

    public PosCheckoutService(
        JdbcTemplate jdbcTemplate,
        JsonMapper jsonMapper,
        PaymentService paymentService
    ) {
        this.jdbcTemplate =
            jdbcTemplate;

        this.jsonMapper =
            jsonMapper;

        this.paymentService =
            paymentService;
    }

    @Transactional
    public PosCheckoutResponse checkout(
        UUID actorId,
        String idempotencyKey,
        PosPaymentRequest request
    ) {

        if (request == null) {
            throw new PosValidationException(
                "POS payment request is required."
            );
        }

        UUID organizationId =
            organizationId(
                actorId
            );

        PosSessionContext session =
            openOwnedSession(
                request.posSessionId(),
                actorId,
                organizationId
            );

        PaymentTarget target =
            paymentTarget(
                request.orderId(),
                organizationId
            );

        if (
            !session
                .locationId()
                .equals(
                    target.locationId()
                )
        ) {
            throw new PosException(
                PosErrorCode.RESOURCE_NOT_FOUND,
                "Order does not belong to this POS location."
            );
        }

        requireSupportedOrder(
            target
        );

        PaymentMethod method =
            paymentMethod(
                request.method()
            );

        Tender tender =
            tender(
                method,
                request.tenderedAmount(),
                target.total()
            );

        PaymentCaptureResult payment =
            paymentService.capture(
                actorId,
                request.orderId(),
                new PaymentCaptureCommand(
                    method,
                    idempotencyKey,
                    target.total(),
                    request.externalReference(),
                    request.posSessionId()
                )
            );

        if (payment.replayed()) {

            PaymentTender persisted =
                paymentTender(
                    payment.paymentId()
                );

            requireReplayTender(
                tender,
                persisted
            );

            PosReceiptResponse receipt =
                existingReceipt(
                    payment.paymentId(),
                    organizationId
                )
                    .orElseThrow(() ->
                        new PosException(
                            PosErrorCode.IDEMPOTENCY_CONFLICT,
                            "Completed POS payment replay has no receipt."
                        )
                    );

            return response(
                payment,
                tender,
                receipt
            );
        }

        persistTender(
            payment,
            tender
        );

        incrementTenderTotal(
            payment.posSessionId(),
            payment.method(),
            payment.amount()
        );

        PosReceiptResponse receipt =
            issueReceipt(
                actorId,
                organizationId,
                session,
                target,
                payment,
                tender
            );

        return response(
            payment,
            tender,
            receipt
        );
    }

    private UUID organizationId(
        UUID actorId
    ) {

        if (actorId == null) {
            throw new BadCredentialsException(
                "Authenticated user is missing."
            );
        }

        List<UUID> organizations =
            jdbcTemplate.query(
                """
                SELECT u.organization_id
                FROM users u
                JOIN organizations o
                  ON o.id = u.organization_id
                WHERE u.id = ?
                  AND u.status = 'ACTIVE'
                  AND o.is_active = TRUE
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    resultSet.getObject(
                        "organization_id",
                        UUID.class
                    ),
                actorId
            );

        if (organizations.isEmpty()) {
            throw new BadCredentialsException(
                "Authenticated user does not exist or is not active."
            );
        }

        return organizations.get(0);
    }

    private PosSessionContext openOwnedSession(
        UUID sessionId,
        UUID actorId,
        UUID organizationId
    ) {

        if (sessionId == null) {
            throw new PosValidationException(
                "posSessionId is required."
            );
        }

        List<PosSessionContext> sessions =
            jdbcTemplate.query(
                """
                SELECT
                    ps.id,
                    ps.terminal_id,
                    l.id AS location_id
                FROM pos_sessions ps
                JOIN pos_terminals pt
                  ON pt.id = ps.terminal_id
                JOIN locations l
                  ON l.id = pt.location_id
                JOIN campuses c
                  ON c.id = l.campus_id
                WHERE ps.id = ?
                  AND ps.cashier_id = ?
                  AND c.organization_id = ?
                  AND ps.status = 'OPEN'
                  AND pt.is_active = TRUE
                  AND pt.terminal_type = 'POS'
                  AND l.is_active = TRUE
                  AND c.is_active = TRUE
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new PosSessionContext(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "terminal_id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "location_id",
                            UUID.class
                        )
                    ),
                sessionId,
                actorId,
                organizationId
            );

        if (sessions.isEmpty()) {
            throw new PosException(
                PosErrorCode.POS_SESSION_NOT_OPEN,
                "POS session does not exist, is not OPEN, or is not owned by this cashier."
            );
        }

        return sessions.get(0);
    }

    private PaymentTarget paymentTarget(
        UUID orderId,
        UUID organizationId
    ) {

        if (orderId == null) {
            throw new PosValidationException(
                "orderId is required."
            );
        }

        List<PaymentTarget> orders =
            jdbcTemplate.query(
                """
                SELECT
                    o.id,
                    o.location_id,
                    o.order_number,
                    o.business_date,
                    o.source,
                    o.order_type,
                    o.total,
                    o.currency
                FROM orders o
                WHERE o.id = ?
                  AND o.organization_id = ?
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new PaymentTarget(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "location_id",
                            UUID.class
                        ),
                        resultSet.getString(
                            "order_number"
                        ),
                        resultSet.getObject(
                            "business_date",
                            LocalDate.class
                        ),
                        resultSet.getString(
                            "source"
                        ),
                        resultSet.getString(
                            "order_type"
                        ),
                        resultSet.getBigDecimal(
                            "total"
                        ),
                        resultSet.getString(
                            "currency"
                        )
                    ),
                orderId,
                organizationId
            );

        if (orders.isEmpty()) {
            throw new PosException(
                PosErrorCode.RESOURCE_NOT_FOUND,
                "Order does not exist."
            );
        }

        return orders.get(0);
    }

    private void requireSupportedOrder(
        PaymentTarget target
    ) {

        boolean direct =
            "POS".equals(
                target.source()
            );

        if (direct) {
            direct =
                "POS_DIRECT".equals(
                    target.orderType()
                );
        }

        boolean mobile =
            "MOBILE".equals(
                target.source()
            );

        if (mobile) {
            mobile =
                "MOBILE_SNACK".equals(
                    target.orderType()
                );
        }

        boolean supported =
            direct || mobile;

        if (!supported) {
            throw new PosException(
                PosErrorCode.RESOURCE_NOT_FOUND,
                "Order cannot be paid through the POS checkout."
            );
        }
    }

    private PaymentMethod paymentMethod(
        PosPaymentMethodRequest method
    ) {

        if (method == null) {
            throw new PosValidationException(
                "Payment method is required."
            );
        }

        return switch (method) {

            case CASH ->
                PaymentMethod.CASH;

            case CARD_TPE ->
                PaymentMethod.CARD_TPE;
        };
    }

    private Tender tender(
        PaymentMethod method,
        BigDecimal requestedTendered,
        BigDecimal amount
    ) {

        BigDecimal normalizedAmount =
            money(
                amount,
                "amount"
            );

        if (
            method
                == PaymentMethod.CASH
        ) {

            if (requestedTendered == null) {
                throw new PosValidationException(
                    "tenderedAmount is required for CASH."
                );
            }

            BigDecimal tendered =
                money(
                    requestedTendered,
                    "tenderedAmount"
                );

            if (
                tendered.compareTo(
                    normalizedAmount
                ) < 0
            ) {
                throw new PosValidationException(
                    "Cash tendered amount is lower than the order total."
                );
            }

            BigDecimal change =
                tendered
                    .subtract(
                        normalizedAmount
                    )
                    .setScale(2);

            return new Tender(
                tendered,
                change
            );
        }

        BigDecimal tendered =
            normalizedAmount;

        if (requestedTendered != null) {

            BigDecimal requested =
                money(
                    requestedTendered,
                    "tenderedAmount"
                );

            if (
                requested.compareTo(
                    normalizedAmount
                ) != 0
            ) {
                throw new PosValidationException(
                    "CARD_TPE tenderedAmount must equal the order total."
                );
            }

            tendered =
                requested;
        }

        return new Tender(
            tendered,
            BigDecimal.ZERO
                .setScale(2)
        );
    }

    private BigDecimal money(
        BigDecimal value,
        String field
    ) {

        if (value == null) {
            throw new PosValidationException(
                field + " is required."
            );
        }

        if (value.signum() < 0) {
            throw new PosValidationException(
                field + " cannot be negative."
            );
        }

        try {

            BigDecimal normalized =
                value.setScale(
                    2,
                    RoundingMode.UNNECESSARY
                );

            if (normalized.precision() > 12) {
                throw new PosValidationException(
                    field + " exceeds NUMERIC(12,2)."
                );
            }

            return normalized;

        } catch (
            ArithmeticException exception
        ) {

            throw new PosValidationException(
                field + " supports at most two decimal places."
            );
        }
    }

    private void persistTender(
        PaymentCaptureResult payment,
        Tender tender
    ) {

        int updated =
            jdbcTemplate.update(
                """
                UPDATE payments
                SET tendered_amount = ?,
                    change_amount = ?
                WHERE id = ?
                  AND order_id = ?
                  AND pos_session_id = ?
                  AND status = 'COMPLETED'
                  AND tendered_amount IS NULL
                  AND change_amount IS NULL
                """,
                tender.tenderedAmount(),
                tender.changeAmount(),
                payment.paymentId(),
                payment.orderId(),
                payment.posSessionId()
            );

        if (updated != 1) {
            throw new IllegalStateException(
                "POS payment tender persistence did not affect exactly one payment."
            );
        }
    }

    private PaymentTender paymentTender(
        UUID paymentId
    ) {

        List<PaymentTender> values =
            jdbcTemplate.query(
                """
                SELECT
                    tendered_amount,
                    change_amount
                FROM payments
                WHERE id = ?
                  AND status = 'COMPLETED'
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new PaymentTender(
                        resultSet.getBigDecimal(
                            "tendered_amount"
                        ),
                        resultSet.getBigDecimal(
                            "change_amount"
                        )
                    ),
                paymentId
            );

        if (values.size() != 1) {
            throw new PosException(
                PosErrorCode.IDEMPOTENCY_CONFLICT,
                "Completed POS payment replay is inconsistent."
            );
        }

        return values.get(0);
    }

    private void requireReplayTender(
        Tender expected,
        PaymentTender persisted
    ) {

        if (
            persisted.tenderedAmount()
                == null
        ) {
            throw replayConflict();
        }

        if (
            persisted.changeAmount()
                == null
        ) {
            throw replayConflict();
        }

        boolean sameTendered =
            persisted
                .tenderedAmount()
                .compareTo(
                    expected.tenderedAmount()
                ) == 0;

        boolean sameChange =
            persisted
                .changeAmount()
                .compareTo(
                    expected.changeAmount()
                ) == 0;

        if (!sameTendered) {
            throw replayConflict();
        }

        if (!sameChange) {
            throw replayConflict();
        }
    }

    private PosException replayConflict() {

        return new PosException(
            PosErrorCode.IDEMPOTENCY_CONFLICT,
            "Idempotency-Key is already used with different POS tender data."
        );
    }

    private void incrementTenderTotal(
        UUID sessionId,
        PaymentMethod method,
        BigDecimal amount
    ) {

        int changed =
            jdbcTemplate.update(
                """
                INSERT INTO pos_session_tender_totals (
                    pos_session_id,
                    payment_method,
                    theoretical_amount
                )
                VALUES (?, ?, ?)
                ON CONFLICT (
                    pos_session_id,
                    payment_method
                )
                DO UPDATE SET
                    theoretical_amount =
                        pos_session_tender_totals.theoretical_amount
                        + EXCLUDED.theoretical_amount
                """,
                sessionId,
                method.name(),
                amount
            );

        if (changed != 1) {
            throw new IllegalStateException(
                "POS tender total update failed."
            );
        }
    }

    private PosReceiptResponse issueReceipt(
        UUID actorId,
        UUID organizationId,
        PosSessionContext session,
        PaymentTarget target,
        PaymentCaptureResult payment,
        Tender tender
    ) {

        Optional<PosReceiptResponse> duplicate =
            existingReceipt(
                payment.paymentId(),
                organizationId
            );

        if (duplicate.isPresent()) {
            throw new PosException(
                PosErrorCode.IDEMPOTENCY_CONFLICT,
                "A receipt already exists for this fresh payment."
            );
        }

        String receiptNumber =
            nextReceiptNumber(
                organizationId,
                session.locationId(),
                target.businessDate()
            );

        UUID receiptId =
            UUID.randomUUID();

        String snapshot =
            receiptSnapshot(
                target,
                payment,
                tender,
                receiptNumber
            );

        int inserted =
            jdbcTemplate.update(
                """
                INSERT INTO sales_receipts (
                    id,
                    organization_id,
                    order_id,
                    payment_id,
                    pos_session_id,
                    receipt_number,
                    business_date,
                    issued_by,
                    status,
                    receipt_snapshot
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, 'ISSUED',
                    CAST(? AS jsonb)
                )
                """,
                receiptId,
                organizationId,
                payment.orderId(),
                payment.paymentId(),
                payment.posSessionId(),
                receiptNumber,
                target.businessDate(),
                actorId,
                snapshot
            );

        if (inserted != 1) {
            throw new IllegalStateException(
                "Sales receipt insert did not affect exactly one row."
            );
        }

        return receipt(
            receiptId,
            organizationId
        );
    }

    private Optional<PosReceiptResponse> existingReceipt(
        UUID paymentId,
        UUID organizationId
    ) {

        List<PosReceiptResponse> receipts =
            jdbcTemplate.query(
                """
                SELECT
                    id,
                    receipt_number,
                    order_id,
                    payment_id,
                    pos_session_id,
                    business_date,
                    issued_at,
                    status
                FROM sales_receipts
                WHERE payment_id = ?
                  AND organization_id = ?
                ORDER BY issued_at ASC, id ASC
                """,
                this::mapReceipt,
                paymentId,
                organizationId
            );

        if (receipts.size() > 1) {
            throw new PosException(
                PosErrorCode.IDEMPOTENCY_CONFLICT,
                "Multiple receipts exist for the same payment."
            );
        }

        return receipts
            .stream()
            .findFirst();
    }

    private PosReceiptResponse receipt(
        UUID receiptId,
        UUID organizationId
    ) {

        List<PosReceiptResponse> receipts =
            jdbcTemplate.query(
                """
                SELECT
                    id,
                    receipt_number,
                    order_id,
                    payment_id,
                    pos_session_id,
                    business_date,
                    issued_at,
                    status
                FROM sales_receipts
                WHERE id = ?
                  AND organization_id = ?
                """,
                this::mapReceipt,
                receiptId,
                organizationId
            );

        if (receipts.size() != 1) {
            throw new IllegalStateException(
                "Issued receipt cannot be reloaded."
            );
        }

        return receipts.get(0);
    }

    private PosReceiptResponse mapReceipt(
        java.sql.ResultSet resultSet,
        int rowNumber
    ) throws java.sql.SQLException {

        return new PosReceiptResponse(
            resultSet.getObject(
                "id",
                UUID.class
            ),
            resultSet.getString(
                "receipt_number"
            ),
            resultSet.getObject(
                "order_id",
                UUID.class
            ),
            resultSet.getObject(
                "payment_id",
                UUID.class
            ),
            resultSet.getObject(
                "pos_session_id",
                UUID.class
            ),
            resultSet.getObject(
                "business_date",
                LocalDate.class
            ),
            resultSet.getObject(
                "issued_at",
                OffsetDateTime.class
            ),
            resultSet.getString(
                "status"
            )
        );
    }

    private String nextReceiptNumber(
        UUID organizationId,
        UUID locationId,
        LocalDate businessDate
    ) {

        Long value =
            jdbcTemplate.queryForObject(
                """
                INSERT INTO document_sequences (
                    organization_id,
                    location_id,
                    sequence_type,
                    business_date,
                    prefix,
                    current_value,
                    padding,
                    updated_at
                )
                VALUES (
                    ?,
                    ?,
                    'RECEIPT',
                    ?,
                    'RCP',
                    1,
                    4,
                    CURRENT_TIMESTAMP
                )
                ON CONFLICT (
                    organization_id,
                    location_id,
                    sequence_type,
                    business_date,
                    prefix
                )
                DO UPDATE SET
                    current_value =
                        document_sequences.current_value + 1,
                    updated_at =
                        CURRENT_TIMESTAMP
                RETURNING current_value
                """,
                Long.class,
                organizationId,
                locationId,
                businessDate
            );

        if (value == null) {
            throw new IllegalStateException(
                "Unable to allocate receipt number."
            );
        }

        return "RCP-"
            + businessDate
                .toString()
                .replace(
                    "-",
                    ""
                )
            + "-"
            + String.format(
                Locale.ROOT,
                "%04d",
                value
            );
    }

    private String receiptSnapshot(
        PaymentTarget target,
        PaymentCaptureResult payment,
        Tender tender,
        String receiptNumber
    ) {

        List<Map<String, Object>> lines =
            jdbcTemplate.queryForList(
                """
                SELECT
                    oi.product_id AS "productId",
                    oi.variant_id AS "variantId",
                    oi.product_name_snapshot AS "productName",
                    oi.variant_name_snapshot AS "variantName",
                    oi.sku_snapshot AS "sku",
                    oi.unit_price AS "unitPrice",
                    oi.quantity AS "quantity",
                    oi.discount_amount AS "discountAmount",
                    oi.line_total AS "lineTotal",
                    oi.tax_rate_snapshot AS "taxRate",
                    oi.line_tax AS "lineTax"
                FROM order_items oi
                WHERE oi.order_id = ?
                ORDER BY oi.id ASC
                """,
                payment.orderId()
            );

        Map<String, Object> snapshot =
            new LinkedHashMap<>();

        snapshot.put(
            "receiptNumber",
            receiptNumber
        );

        snapshot.put(
            "orderId",
            payment.orderId()
        );

        snapshot.put(
            "orderNumber",
            target.orderNumber()
        );

        snapshot.put(
            "orderSource",
            target.source()
        );

        snapshot.put(
            "orderType",
            target.orderType()
        );

        snapshot.put(
            "paymentId",
            payment.paymentId()
        );

        snapshot.put(
            "posSessionId",
            payment.posSessionId()
        );

        snapshot.put(
            "paymentMethod",
            payment.method().name()
        );

        snapshot.put(
            "amount",
            payment.amount()
        );

        snapshot.put(
            "currency",
            payment.currency()
        );

        snapshot.put(
            "tenderedAmount",
            tender.tenderedAmount()
        );

        snapshot.put(
            "changeAmount",
            tender.changeAmount()
        );

        snapshot.put(
            "paidAt",
            payment.paidAt()
        );

        snapshot.put(
            "items",
            lines
        );

        try {

            return jsonMapper
                .writeValueAsString(
                    snapshot
                );

        } catch (Exception exception) {

            throw new IllegalStateException(
                "Unable to serialize receipt snapshot.",
                exception
            );
        }
    }

    private PosCheckoutResponse response(
        PaymentCaptureResult payment,
        Tender tender,
        PosReceiptResponse receipt
    ) {

        return new PosCheckoutResponse(
            payment.paymentId(),
            payment.orderId(),
            payment.posSessionId(),
            payment.method().name(),
            payment.status().name(),
            payment.amount(),
            payment.currency(),
            tender.tenderedAmount(),
            tender.changeAmount(),
            payment.paidAt(),
            payment.replayed(),
            receipt
        );
    }

    private record PosSessionContext(
        UUID id,
        UUID terminalId,
        UUID locationId
    ) {
    }

    private record PaymentTarget(
        UUID id,
        UUID locationId,
        String orderNumber,
        LocalDate businessDate,
        String source,
        String orderType,
        BigDecimal total,
        String currency
    ) {
    }

    private record Tender(
        BigDecimal tenderedAmount,
        BigDecimal changeAmount
    ) {
    }

    private record PaymentTender(
        BigDecimal tenderedAmount,
        BigDecimal changeAmount
    ) {
    }
}