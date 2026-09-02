package com.sup2i.food.payment.service;

import com.sup2i.food.payment.api.dto.RefundRequest;
import com.sup2i.food.payment.api.dto.RefundResponse;
import com.sup2i.food.payment.exception.RefundErrorCode;
import com.sup2i.food.payment.exception.RefundException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentRefundService {

    private static final int RESPONSE_STATUS_CREATED =
        201;

    private static final String RESOURCE_TYPE =
        "PAYMENT_REFUND";

    private static final Duration IDEMPOTENCY_RETENTION =
        Duration.ofHours(24);

    private final JdbcTemplate jdbcTemplate;

    public PaymentRefundService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            jdbcTemplate;
    }

    @Transactional
    public RefundResponse refund(
        UUID actorId,
        UUID paymentId,
        String rawIdempotencyKey,
        RefundRequest request
    ) {
        if (paymentId == null) {
            throw validation(
                "paymentId is required."
            );
        }

        if (
            request == null
            || request.amount() == null
        ) {
            throw validation(
                "Refund amount is required."
            );
        }

        BigDecimal amount =
            money(
                request.amount()
            );

        if (
            amount.compareTo(
                BigDecimal.ZERO
            ) <= 0
        ) {
            throw validation(
                "Refund amount must be positive."
            );
        }

        String reason =
            normalizeReason(
                request.reason()
            );

        UUID organizationId =
            organizationId(
                actorId
            );

        String idempotencyKey =
            normalizeIdempotencyKey(
                rawIdempotencyKey
            );

        String scope =
            "PAYMENT_REFUND:"
                + organizationId
                + ":"
                + paymentId;

        String requestHash =
            requestHash(
                actorId,
                paymentId,
                amount,
                reason
            );

        OffsetDateTime now =
            OffsetDateTime.now();

        lockIdempotency(
            scope,
            idempotencyKey
        );

        deleteExpired(
            scope,
            idempotencyKey,
            now
        );

        Optional<StoredIdempotency> stored =
            stored(
                scope,
                idempotencyKey
            );

        if (stored.isPresent()) {
            StoredIdempotency replay =
                stored.get();

            validateReplay(
                replay,
                actorId,
                requestHash
            );

            return refundResponse(
                organizationId,
                paymentId,
                replay.resourceId()
            );
        }

        PaymentRow payment =
            lockPayment(
                organizationId,
                paymentId
            );

        boolean refundableStatus =
            "COMPLETED".equals(
                payment.status()
            )
                || "PARTIALLY_REFUNDED".equals(
                    payment.status()
                );

        if (!refundableStatus) {
            throw new RefundException(
                RefundErrorCode.PAYMENT_NOT_REFUNDABLE,
                "Payment is not refundable in its current state."
            );
        }

        BigDecimal alreadyRefunded =
            completedRefundAmount(
                paymentId
            );

        BigDecimal remaining =
            money(
                payment.amount()
                    .subtract(
                        alreadyRefunded
                    )
            );

        if (
            remaining.compareTo(
                BigDecimal.ZERO
            ) <= 0
        ) {
            throw new RefundException(
                RefundErrorCode.PAYMENT_NOT_REFUNDABLE,
                "Payment has already been fully refunded."
            );
        }

        if (
            amount.compareTo(
                remaining
            ) > 0
        ) {
            throw new RefundException(
                RefundErrorCode.REFUND_AMOUNT_EXCEEDED,
                "Refund amount exceeds the remaining refundable payment amount."
            );
        }

        UUID refundId =
            UUID.randomUUID();

        int refundInserted =
            jdbcTemplate.update(
                """
                INSERT INTO refunds (
                    id,
                    payment_id,
                    amount,
                    reason,
                    status,
                    requested_by,
                    approved_by,
                    created_at,
                    completed_at
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    ?,
                    'COMPLETED',
                    ?,
                    ?,
                    ?,
                    ?
                )
                """,
                refundId,
                paymentId,
                amount,
                reason,
                actorId,
                actorId,
                now,
                now
            );

        if (refundInserted != 1) {
            throw new IllegalStateException(
                "Refund insert failed."
            );
        }

        insertRefundEvent(
            refundId,
            "REQUESTED",
            null,
            "REQUESTED",
            actorId,
            reason
        );

        insertRefundEvent(
            refundId,
            "COMPLETED",
            "REQUESTED",
            "COMPLETED",
            actorId,
            reason
        );

        boolean paymentFullyRefunded =
            amount.compareTo(
                remaining
            ) == 0;

        String paymentStatus =
            paymentFullyRefunded
                ? "REFUNDED"
                : "PARTIALLY_REFUNDED";

        int paymentUpdated =
            jdbcTemplate.update(
                """
                UPDATE payments
                SET status = ?,
                    reversed_at = CASE
                        WHEN ? = 'REFUNDED'
                        THEN ?
                        ELSE reversed_at
                    END,
                    updated_at = ?
                WHERE id = ?
                """,
                paymentStatus,
                paymentStatus,
                now,
                now,
                paymentId
            );

        if (paymentUpdated != 1) {
            throw new IllegalStateException(
                "Payment refund status update failed."
            );
        }

        OrderRefundTotals orderTotals =
            orderRefundTotals(
                payment.orderId()
            );

        boolean orderFullyRefunded =
            orderTotals.paidAmount()
                .compareTo(
                    BigDecimal.ZERO
                ) > 0
                && orderTotals.refundedAmount()
                    .compareTo(
                        orderTotals.paidAmount()
                    ) >= 0;

        if (orderFullyRefunded) {
            markOrderFullyRefunded(
                payment,
                actorId,
                now
            );
        }
        else {
            markOrderPartiallyRefunded(
                payment.orderId(),
                organizationId
            );
        }

        auditRefund(
            organizationId,
            actorId,
            refundId,
            paymentId,
            amount,
            reason,
            paymentStatus,
            orderFullyRefunded
        );

        persistIdempotency(
            scope,
            idempotencyKey,
            actorId,
            requestHash,
            refundId,
            paymentId,
            amount,
            reason,
            now.plus(
                IDEMPOTENCY_RETENTION
            )
        );

        return new RefundResponse(
            refundId,
            paymentId,
            amount,
            "COMPLETED",
            reason
        );
    }

    private UUID organizationId(
        UUID actorId
    ) {
        if (actorId == null) {
            throw new BadCredentialsException(
                "Authenticated user identifier is missing."
            );
        }

        List<UUID> rows =
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
                (resultSet, rowNumber) ->
                    resultSet.getObject(
                        "organization_id",
                        UUID.class
                    ),
                actorId
            );

        if (rows.size() != 1) {
            throw new BadCredentialsException(
                "Authenticated user does not exist or is inactive."
            );
        }

        return rows.getFirst();
    }

    private PaymentRow lockPayment(
        UUID organizationId,
        UUID paymentId
    ) {
        List<PaymentRow> rows =
            jdbcTemplate.query(
                """
                SELECT
                    p.id,
                    p.order_id,
                    p.status,
                    p.amount,
                    p.currency,
                    o.status AS order_status,
                    o.payment_status AS order_payment_status
                FROM payments p
                JOIN orders o
                  ON o.id = p.order_id
                WHERE p.id = ?
                  AND o.organization_id = ?
                FOR UPDATE OF p, o
                """,
                (resultSet, rowNumber) ->
                    new PaymentRow(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "order_id",
                            UUID.class
                        ),
                        resultSet.getString(
                            "status"
                        ),
                        money(
                            resultSet.getBigDecimal(
                                "amount"
                            )
                        ),
                        resultSet.getString(
                            "currency"
                        ),
                        resultSet.getString(
                            "order_status"
                        ),
                        resultSet.getString(
                            "order_payment_status"
                        )
                    ),
                paymentId,
                organizationId
            );

        if (rows.isEmpty()) {
            throw new RefundException(
                RefundErrorCode.RESOURCE_NOT_FOUND,
                "Payment does not exist."
            );
        }

        if (rows.size() != 1) {
            throw new IllegalStateException(
                "Payment refund lookup returned multiple rows."
            );
        }

        return rows.getFirst();
    }

    private BigDecimal completedRefundAmount(
        UUID paymentId
    ) {
        BigDecimal value =
            jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(
                    SUM(amount),
                    0
                )
                FROM refunds
                WHERE payment_id = ?
                  AND status = 'COMPLETED'
                """,
                BigDecimal.class,
                paymentId
            );

        return money(
            value
        );
    }

    private OrderRefundTotals orderRefundTotals(
        UUID orderId
    ) {
        List<OrderRefundTotals> rows =
            jdbcTemplate.query(
                """
                SELECT
                    COALESCE(
                        (
                            SELECT SUM(p.amount)
                            FROM payments p
                            WHERE p.order_id = ?
                              AND p.status IN (
                                    'COMPLETED',
                                    'PARTIALLY_REFUNDED',
                                    'REFUNDED'
                              )
                        ),
                        0
                    ) AS paid_amount,
                    COALESCE(
                        (
                            SELECT SUM(r.amount)
                            FROM refunds r
                            JOIN payments p
                              ON p.id = r.payment_id
                            WHERE p.order_id = ?
                              AND r.status = 'COMPLETED'
                        ),
                        0
                    ) AS refunded_amount
                """,
                (resultSet, rowNumber) ->
                    new OrderRefundTotals(
                        money(
                            resultSet.getBigDecimal(
                                "paid_amount"
                            )
                        ),
                        money(
                            resultSet.getBigDecimal(
                                "refunded_amount"
                            )
                        )
                    ),
                orderId,
                orderId
            );

        if (rows.size() != 1) {
            throw new IllegalStateException(
                "Order refund total calculation failed."
            );
        }

        return rows.getFirst();
    }

    private void markOrderFullyRefunded(
        PaymentRow payment,
        UUID actorId,
        OffsetDateTime now
    ) {
        String previousStatus =
            payment.orderStatus();

        if (!"REFUNDED".equals(previousStatus)) {
            int historyInserted =
                jdbcTemplate.update(
                    """
                    INSERT INTO order_status_history (
                        id,
                        order_id,
                        from_status,
                        to_status,
                        changed_by,
                        reason,
                        source,
                        created_at
                    )
                    VALUES (
                        ?,
                        ?,
                        ?,
                        'REFUNDED',
                        ?,
                        'Payment fully refunded.',
                        'API',
                        ?
                    )
                    """,
                    UUID.randomUUID(),
                    payment.orderId(),
                    previousStatus,
                    actorId,
                    now
                );

            if (historyInserted != 1) {
                throw new IllegalStateException(
                    "Refunded order history insert failed."
                );
            }
        }

        int updated =
            jdbcTemplate.update(
                """
                UPDATE orders
                SET status = 'REFUNDED',
                    payment_status = 'REFUNDED',
                    version = version + 1,
                    updated_at = ?
                WHERE id = ?
                """,
                now,
                payment.orderId()
            );

        if (updated != 1) {
            throw new IllegalStateException(
                "Fully refunded order update failed."
            );
        }
    }

    private void markOrderPartiallyRefunded(
        UUID orderId,
        UUID organizationId
    ) {
        int updated =
            jdbcTemplate.update(
                """
                UPDATE orders
                SET payment_status = 'PARTIALLY_REFUNDED',
                    version = version + 1,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND organization_id = ?
                  AND payment_status <> 'REFUNDED'
                """,
                orderId,
                organizationId
            );

        if (updated != 1) {
            throw new IllegalStateException(
                "Partially refunded order update failed."
            );
        }
    }

    private void insertRefundEvent(
        UUID refundId,
        String eventType,
        String beforeStatus,
        String afterStatus,
        UUID actorId,
        String reason
    ) {
        int inserted =
            jdbcTemplate.update(
                """
                INSERT INTO refund_events (
                    id,
                    refund_id,
                    event_type,
                    status_before,
                    status_after,
                    performed_by,
                    payload,
                    occurred_at
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    jsonb_build_object(
                        'reason',
                        ?
                    ),
                    CURRENT_TIMESTAMP
                )
                """,
                UUID.randomUUID(),
                refundId,
                eventType,
                beforeStatus,
                afterStatus,
                actorId,
                reason
            );

        if (inserted != 1) {
            throw new IllegalStateException(
                "Refund lifecycle event insert failed."
            );
        }
    }

    private void auditRefund(
        UUID organizationId,
        UUID actorId,
        UUID refundId,
        UUID paymentId,
        BigDecimal amount,
        String reason,
        String paymentStatus,
        boolean orderFullyRefunded
    ) {
        int inserted =
            jdbcTemplate.update(
                """
                INSERT INTO audit_logs (
                    id,
                    organization_id,
                    user_id,
                    action,
                    resource_type,
                    resource_id,
                    after_data,
                    reason,
                    source,
                    result
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    'PAYMENT_REFUND_COMPLETED',
                    'REFUND',
                    ?,
                    jsonb_build_object(
                        'refundId',
                        ?::text,
                        'paymentId',
                        ?::text,
                        'amount',
                        ?,
                        'refundStatus',
                        'COMPLETED',
                        'paymentStatus',
                        ?,
                        'orderFullyRefunded',
                        ?
                    ),
                    ?,
                    'API',
                    'SUCCESS'
                )
                """,
                UUID.randomUUID(),
                organizationId,
                actorId,
                refundId,
                refundId,
                paymentId,
                amount,
                paymentStatus,
                orderFullyRefunded,
                reason
            );

        if (inserted != 1) {
            throw new IllegalStateException(
                "Payment refund audit insert failed."
            );
        }
    }

    private RefundResponse refundResponse(
        UUID organizationId,
        UUID paymentId,
        UUID refundId
    ) {
        List<RefundResponse> rows =
            jdbcTemplate.query(
                """
                SELECT
                    r.id,
                    r.payment_id,
                    r.amount,
                    r.status,
                    r.reason
                FROM refunds r
                JOIN payments p
                  ON p.id = r.payment_id
                JOIN orders o
                  ON o.id = p.order_id
                WHERE r.id = ?
                  AND r.payment_id = ?
                  AND o.organization_id = ?
                """,
                (resultSet, rowNumber) ->
                    new RefundResponse(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "payment_id",
                            UUID.class
                        ),
                        money(
                            resultSet.getBigDecimal(
                                "amount"
                            )
                        ),
                        resultSet.getString(
                            "status"
                        ),
                        resultSet.getString(
                            "reason"
                        )
                    ),
                refundId,
                paymentId,
                organizationId
            );

        if (rows.size() != 1) {
            throw new RefundException(
                RefundErrorCode.RESOURCE_NOT_FOUND,
                "Refund does not exist."
            );
        }

        return rows.getFirst();
    }

    private String normalizeReason(
        String raw
    ) {
        if (raw == null) {
            throw validation(
                "Refund reason is required."
            );
        }

        String value =
            raw.trim();

        if (value.length() < 3) {
            throw validation(
                "Refund reason must contain at least 3 characters."
            );
        }

        if (value.length() > 500) {
            throw validation(
                "Refund reason cannot exceed 500 characters."
            );
        }

        return value;
    }

    private String normalizeIdempotencyKey(
        String raw
    ) {
        if (raw == null) {
            throw validation(
                "Idempotency-Key header is required."
            );
        }

        String value =
            raw.trim();

        if (value.length() < 8) {
            throw validation(
                "Idempotency-Key must contain at least 8 characters."
            );
        }

        if (value.length() > 160) {
            throw validation(
                "Idempotency-Key cannot exceed 160 characters."
            );
        }

        return value;
    }

    private void lockIdempotency(
        String scope,
        String key
    ) {
        byte[] digest =
            sha256Bytes(
                "PAYMENT_REFUND_IDEMPOTENCY"
                    + "\n"
                    + scope
                    + "\n"
                    + key
            );

        long lockKey =
            ByteBuffer
                .wrap(
                    digest
                )
                .getLong();

        jdbcTemplate.query(
            "SELECT pg_advisory_xact_lock(?)",
            statement ->
                statement.setLong(
                    1,
                    lockKey
                ),
            (ResultSetExtractor<Void>)
                resultSet ->
                    null
        );
    }

    private void deleteExpired(
        String scope,
        String key,
        OffsetDateTime now
    ) {
        jdbcTemplate.update(
            """
            DELETE FROM idempotency_records
            WHERE scope = ?
              AND idempotency_key = ?
              AND expires_at <= ?
            """,
            scope,
            key,
            now
        );
    }

    private Optional<StoredIdempotency> stored(
        String scope,
        String key
    ) {
        List<StoredIdempotency> rows =
            jdbcTemplate.query(
                """
                SELECT
                    user_id,
                    request_hash,
                    response_status,
                    resource_type,
                    resource_id
                FROM idempotency_records
                WHERE scope = ?
                  AND idempotency_key = ?
                """,
                (resultSet, rowNumber) ->
                    new StoredIdempotency(
                        resultSet.getObject(
                            "user_id",
                            UUID.class
                        ),
                        resultSet.getString(
                            "request_hash"
                        ),
                        resultSet.getInt(
                            "response_status"
                        ),
                        resultSet.getString(
                            "resource_type"
                        ),
                        resultSet.getObject(
                            "resource_id",
                            UUID.class
                        )
                    ),
                scope,
                key
            );

        if (rows.size() > 1) {
            throw new IllegalStateException(
                "Refund idempotency lookup returned multiple rows."
            );
        }

        return rows
            .stream()
            .findFirst();
    }

    private void validateReplay(
        StoredIdempotency stored,
        UUID actorId,
        String requestHash
    ) {
        boolean sameUser =
            actorId.equals(
                stored.userId()
            );

        boolean sameRequest =
            requestHash.equals(
                stored.requestHash()
            );

        boolean sameStatus =
            stored.responseStatus()
                == RESPONSE_STATUS_CREATED;

        boolean sameType =
            RESOURCE_TYPE.equals(
                stored.resourceType()
            );

        boolean hasResource =
            stored.resourceId() != null;

        boolean valid =
            sameUser
                && sameRequest
                && sameStatus
                && sameType
                && hasResource;

        if (!valid) {
            throw new RefundException(
                RefundErrorCode.IDEMPOTENCY_CONFLICT,
                "Idempotency-Key was already used for another refund request."
            );
        }
    }

    private void persistIdempotency(
        String scope,
        String key,
        UUID actorId,
        String requestHash,
        UUID refundId,
        UUID paymentId,
        BigDecimal amount,
        String reason,
        OffsetDateTime expiresAt
    ) {
        int inserted =
            jdbcTemplate.update(
                """
                INSERT INTO idempotency_records (
                    id,
                    idempotency_key,
                    scope,
                    user_id,
                    request_hash,
                    response_status,
                    response_body,
                    resource_type,
                    resource_id,
                    expires_at
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    jsonb_build_object(
                        'id',
                        ?::text,
                        'paymentId',
                        ?::text,
                        'amount',
                        ?,
                        'status',
                        'COMPLETED',
                        'reason',
                        ?
                    ),
                    ?,
                    ?,
                    ?
                )
                """,
                UUID.randomUUID(),
                key,
                scope,
                actorId,
                requestHash,
                RESPONSE_STATUS_CREATED,
                refundId,
                paymentId,
                amount,
                reason,
                RESOURCE_TYPE,
                refundId,
                expiresAt
            );

        if (inserted != 1) {
            throw new IllegalStateException(
                "Refund idempotency insert failed."
            );
        }
    }

    private String requestHash(
        UUID actorId,
        UUID paymentId,
        BigDecimal amount,
        String reason
    ) {
        String canonical =
            "PAYMENT_REFUND"
                + "\n"
                + actorId
                + "\n"
                + paymentId
                + "\n"
                + amount.toPlainString()
                + "\n"
                + reason;

        byte[] digest =
            sha256Bytes(
                canonical
            );

        StringBuilder hex =
            new StringBuilder(
                digest.length * 2
            );

        for (byte value : digest) {
            hex.append(
                String.format(
                    "%02x",
                    value & 0xff
                )
            );
        }

        return hex.toString();
    }

    private byte[] sha256Bytes(
        String value
    ) {
        try {
            MessageDigest digest =
                MessageDigest.getInstance(
                    "SHA-256"
                );

            return digest.digest(
                value.getBytes(
                    StandardCharsets.UTF_8
                )
            );
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                "SHA-256 is unavailable.",
                exception
            );
        }
    }

    private RefundException validation(
        String message
    ) {
        return new RefundException(
            RefundErrorCode.VALIDATION_ERROR,
            message
        );
    }

    private static BigDecimal money(
        BigDecimal value
    ) {
        if (value == null) {
            return BigDecimal.ZERO
                .setScale(2);
        }

        return value.setScale(
            2,
            RoundingMode.HALF_UP
        );
    }

    private record PaymentRow(
        UUID id,
        UUID orderId,
        String status,
        BigDecimal amount,
        String currency,
        String orderStatus,
        String orderPaymentStatus
    ) {
    }

    private record OrderRefundTotals(
        BigDecimal paidAmount,
        BigDecimal refundedAmount
    ) {
    }

    private record StoredIdempotency(
        UUID userId,
        String requestHash,
        int responseStatus,
        String resourceType,
        UUID resourceId
    ) {
    }
}