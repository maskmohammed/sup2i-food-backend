package com.sup2i.food.payment.service;
import org.springframework.beans.factory.annotation.Autowired;
import com.sup2i.food.notification.service.OrderNotificationDispatchService;

import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.inventory.domain.InventoryMovement;
import com.sup2i.food.inventory.domain.InventoryMovementLot;
import com.sup2i.food.inventory.domain.InventoryMovementType;
import com.sup2i.food.inventory.domain.StockBalance;
import com.sup2i.food.inventory.domain.StockBalanceId;
import com.sup2i.food.inventory.domain.StockItem;
import com.sup2i.food.inventory.domain.StockLot;
import com.sup2i.food.inventory.repository.InventoryMovementLotRepository;
import com.sup2i.food.inventory.repository.InventoryMovementRepository;
import com.sup2i.food.inventory.repository.StockBalanceRepository;
import com.sup2i.food.inventory.repository.StockLotRepository;
import com.sup2i.food.inventory.service.InventoryAlertService;
import com.sup2i.food.order.domain.Order;
import com.sup2i.food.order.domain.OrderItem;
import com.sup2i.food.order.domain.OrderPaymentStatus;
import com.sup2i.food.order.domain.OrderStatus;
import com.sup2i.food.order.domain.OrderStatusHistory;
import com.sup2i.food.order.domain.OrderStatusHistorySource;
import com.sup2i.food.order.domain.StockReservation;
import com.sup2i.food.payment.exception.PaymentErrorCode;
import com.sup2i.food.payment.exception.PaymentNotFoundException;
import com.sup2i.food.order.repository.OrderRepository;
import com.sup2i.food.order.repository.OrderStatusHistoryRepository;
import com.sup2i.food.order.repository.StockReservationRepository;
import com.sup2i.food.payment.domain.Payment;
import com.sup2i.food.payment.domain.PaymentEvent;
import com.sup2i.food.payment.domain.PaymentMethod;
import com.sup2i.food.payment.domain.PaymentStatus;
import com.sup2i.food.payment.exception.PaymentConflictException;
import com.sup2i.food.payment.exception.PaymentValidationException;
import com.sup2i.food.payment.repository.PaymentEventRepository;
import com.sup2i.food.payment.repository.PaymentRepository;
import com.sup2i.food.payment.service.port.PaidOrderKitchenQueue;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class PaymentService {

    private static final String
        PAYMENT_REFERENCE =
            "PAYMENT";

    private static final String
        PAYMENT_EVENT_CAPTURE_COMPLETED =
            "CAPTURE_COMPLETED";

    private static final int
        IDEMPOTENCY_KEY_MAX_LENGTH =
            160;

    private final UserRepository userRepository;

    private final OrderRepository orderRepository;

    private final OrderStatusHistoryRepository
        historyRepository;

    private final StockReservationRepository
        reservationRepository;

    private final StockBalanceRepository
        stockBalanceRepository;

    private final StockLotRepository
        stockLotRepository;

    private final InventoryMovementRepository
        movementRepository;

    private final InventoryMovementLotRepository
        movementLotRepository;

    private final PaymentRepository
        paymentRepository;

    private final PaymentEventRepository
        paymentEventRepository;

    private final InventoryAlertService
        inventoryAlertService;

    private final PaidOrderKitchenQueue
        paidOrderKitchenQueue;

    private final JdbcTemplate jdbcTemplate;
    private OrderNotificationDispatchService
        notificationDispatchService;

    @Autowired
    void setNotificationDispatchService(
        OrderNotificationDispatchService notificationDispatchService
    ) {
        this.notificationDispatchService =
            notificationDispatchService;
    }


    public PaymentService(
        UserRepository userRepository,
        OrderRepository orderRepository,
        OrderStatusHistoryRepository
            historyRepository,
        StockReservationRepository
            reservationRepository,
        StockBalanceRepository
            stockBalanceRepository,
        StockLotRepository
            stockLotRepository,
        InventoryMovementRepository
            movementRepository,
        InventoryMovementLotRepository
            movementLotRepository,
        PaymentRepository
            paymentRepository,
        PaymentEventRepository
            paymentEventRepository,
        InventoryAlertService
            inventoryAlertService,
        PaidOrderKitchenQueue
            paidOrderKitchenQueue,
        JdbcTemplate jdbcTemplate
    ) {
        this.userRepository =
            userRepository;

        this.orderRepository =
            orderRepository;

        this.historyRepository =
            historyRepository;

        this.reservationRepository =
            reservationRepository;

        this.stockBalanceRepository =
            stockBalanceRepository;

        this.stockLotRepository =
            stockLotRepository;

        this.movementRepository =
            movementRepository;

        this.movementLotRepository =
            movementLotRepository;

        this.paymentRepository =
            paymentRepository;

        this.paymentEventRepository =
            paymentEventRepository;

        this.inventoryAlertService =
            inventoryAlertService;

        this.paidOrderKitchenQueue =
            paidOrderKitchenQueue;

        this.jdbcTemplate =
            jdbcTemplate;
    }

    @Transactional
    public PaymentCaptureResult capture(
        UUID actorId,
        UUID orderId,
        PaymentCaptureCommand command
    ) {

        if (orderId == null) {
            throw new PaymentValidationException(
                "Order identifier is required."
            );
        }

        User actor =
            authenticatedUser(
                actorId
            );

        NormalizedCapture capture =
            normalize(
                command
            );

        /*
         * Serialize identical idempotency keys before the
         * unique payments row exists.
         */
        lockIdempotencyKey(
            capture.idempotencyKey()
        );

        Payment existing =
            paymentRepository
                .findByIdempotencyKey(
                    capture.idempotencyKey()
                )
                .orElse(null);

        if (existing != null) {
            return replayExisting(
                actor,
                orderId,
                capture,
                existing
            );
        }

        UUID organizationId =
            actor
                .getOrganization()
                .getId();

        /*
         * Different idempotency keys targeting the same
         * order are serialized here.
         */
        Order order =
            orderRepository
                .findOwnedByIdForUpdate(
                    orderId,
                    organizationId
                )
                .orElseThrow(() ->
                    new PaymentNotFoundException(
                        PaymentErrorCode.ORDER_NOT_FOUND,
                        "Order does not exist."
                    )
                );

        OffsetDateTime now =
            OffsetDateTime.now();

        validatePayableOrder(
            order,
            now
        );

        BigDecimal amount =
            order.getTotal();

        validateRequestedAmount(
            capture.amount(),
            amount
        );

        validatePosSession(
            capture.posSessionId(),
            organizationId
        );

        /*
         * Persist PENDING first so its UUID can be the
         * immutable stock-movement reference.
         *
         * Any later failure rolls this insert back because
         * the entire capture is one transaction.
         */
        Payment payment =
            paymentRepository
                .saveAndFlush(
                    new Payment(
                        order,
                        capture.posSessionId(),
                        capture.method(),
                        amount,
                        order.getCurrency(),
                        capture.externalReference(),
                        capture.idempotencyKey(),
                        actor
                    )
                );

        List<StockReservation> reservations =
            reservationRepository
                .findActiveByOrderForUpdate(
                    order.getId()
                );

        boolean physicalStockChanged =
            consumePackagedReservations(
                order,
                payment,
                reservations,
                actor,
                now
            );

        PaymentStatus paymentStatusBefore =
            payment.getStatus();

        payment.complete(
            now
        );

        paymentRepository.save(
            payment
        );

        paymentEventRepository.save(
            new PaymentEvent(
                payment,
                PAYMENT_EVENT_CAPTURE_COMPLETED,
                paymentStatusBefore,
                PaymentStatus.COMPLETED,
                capture.externalReference(),
                null,
                Map.of(
                    "orderId",
                    order.getId()
                        .toString(),
                    "method",
                    capture.method()
                        .name()
                )
            )
        );

        OrderStatus orderStatusBefore =
            order.getStatus();

        order.markPaid(
            now
        );

        historyRepository.save(
            new OrderStatusHistory(
                order,
                orderStatusBefore,
                OrderStatus.PAID,
                actor,
                "Payment completed.",
                OrderStatusHistorySource.API
            )
        );

        orderRepository.saveAndFlush(
            order
        );

        /*
         * Force DB invariants before returning.
         */
        reservationRepository.flush();

        stockBalanceRepository.flush();

        stockLotRepository.flush();

        movementRepository.flush();

        movementLotRepository.flush();

        paymentEventRepository.flush();

        paymentRepository.flush();

        if (physicalStockChanged) {

            inventoryAlertService
                .reconcileOrganization(
                    organizationId
                );
        }

        /*
         * A fresh completed payment must become operationally
         * visible to Kitchen in the SAME transaction.
         *
         * The payment side depends only on its narrow port.
         * The Kitchen adapter joins this transaction with
         * Propagation.MANDATORY.
         *
         * Any routing/ticket failure therefore rolls back:
         * payment, payment event, PAID history, stock effects,
         * and the attempted Kitchen queue transition together.
         */
        paidOrderKitchenQueue.enqueuePaidOrder(
            organizationId,
            order.getId(),
            now
        );

        if (notificationDispatchService != null) {
            notificationDispatchService
                .paymentConfirmedAfterCommit(
                    order
                );
        }

        return result(
            payment,
            false
        );
    }

    private boolean consumePackagedReservations(
        Order order,
        Payment payment,
        List<StockReservation> reservations,
        User actor,
        OffsetDateTime now
    ) {

        boolean changed =
            false;

        for (
            StockReservation reservation
            : reservations
        ) {

            OrderItem orderItem =
                reservation.getOrderItem();

            if (orderItem == null) {
                throw new PaymentConflictException(
                    "Order stock reservation is not linked to an order item."
                );
            }

            StockItem stockItem =
                reservation.getStockItem();

            boolean prepared =
                orderItem
                    .getProduct()
                    .isPrepared();

            /*
             * Prepared ingredients stay reserved at PAID.
             * They will be physically consumed at PREPARING.
             */
            if (prepared) {

                if (
                    stockItem.getIngredient()
                        == null
                ) {
                    throw new PaymentConflictException(
                        "Prepared reservation must reference ingredient stock."
                    );
                }

                continue;
            }

            if (
                stockItem.getIngredient()
                    != null
            ) {
                throw new PaymentConflictException(
                    "Packaged reservation cannot reference ingredient stock."
                );
            }

            StockBalance balance =
                stockBalanceRepository
                    .findLockedById(
                        new StockBalanceId(
                            stockItem.getId(),
                            reservation
                                .getStockLocation()
                                .getId()
                        )
                    )
                    .orElseThrow(() ->
                        new PaymentConflictException(
                            "Reserved stock balance does not exist."
                        )
                    );

            BigDecimal quantity =
                reservation.getQuantity();

            /*
             * PAID packaged consumption:
             *
             * physical -= q
             * reserved -= q
             *
             * available remains unchanged.
             */
            balance.consumeReserved(
                quantity
            );

            InventoryMovement movement =
                movementRepository.save(
                    new InventoryMovement(
                        stockItem,
                        reservation
                            .getStockLocation(),
                        InventoryMovementType
                            .SALE_OUT,
                        quantity.negate(),
                        quantity.negate(),
                        stockItem
                            .getBaseUnit(),
                        null,
                        PAYMENT_REFERENCE,
                        payment.getId(),
                        "Order payment stock consumption",
                        order.getOrderNumber(),
                        actor
                    )
                );

            consumeLotsFefo(
                stockItem,
                reservation,
                movement,
                quantity
            );

            reservation.consume(
                now
            );

            stockBalanceRepository.save(
                balance
            );

            reservationRepository.save(
                reservation
            );

            changed =
                true;
        }

        return changed;
    }

    private void consumeLotsFefo(
        StockItem stockItem,
        StockReservation reservation,
        InventoryMovement movement,
        BigDecimal requestedQuantity
    ) {

        List<StockLot> lots =
            stockLotRepository
                .findConsumableLotsForUpdate(
                    stockItem.getId(),
                    reservation
                        .getStockLocation()
                        .getId()
                );

        BigDecimal remaining =
            requestedQuantity;

        for (
            StockLot lot
            : lots
        ) {

            if (remaining.signum() == 0) {
                break;
            }

            BigDecimal allocated =
                lot
                    .getQuantityRemaining()
                    .min(
                        remaining
                    );

            if (
                allocated.signum()
                    <= 0
            ) {
                continue;
            }

            lot.consume(
                allocated
            );

            stockLotRepository.save(
                lot
            );

            movementLotRepository.save(
                new InventoryMovementLot(
                    movement,
                    lot,
                    allocated.negate()
                )
            );

            remaining =
                remaining.subtract(
                    allocated
                );
        }

        /*
         * Existing Inventory invariant:
         * expiry-tracked stock must be fully represented
         * by stock lots.
         */
        boolean expiryCoverageMissing =
            stockItem.isTrackExpiry();

        if (
            expiryCoverageMissing
        ) {

            if (
                remaining.signum()
                    > 0
            ) {
                throw new PaymentConflictException(
                    "Expiry-tracked stock is not fully represented by lots."
                );
            }
        }
    }

    private PaymentCaptureResult replayExisting(
        User actor,
        UUID requestedOrderId,
        NormalizedCapture capture,
        Payment payment
    ) {

        Order existingOrder =
            payment.getOrder();

        UUID actorOrganizationId =
            actor
                .getOrganization()
                .getId();

        UUID paymentOrganizationId =
            existingOrder
                .getOrganization()
                .getId();

        if (
            !paymentOrganizationId.equals(
                actorOrganizationId
            )
        ) {
            throw new PaymentNotFoundException(
                        PaymentErrorCode.ORDER_NOT_FOUND,
                        "Order does not exist."
                    );
        }

        boolean sameOrder =
            existingOrder
                .getId()
                .equals(
                    requestedOrderId
                );

        boolean sameMethod =
            payment.getMethod()
                == capture.method();

        boolean sameRequestedAmount =
            sameAmount(
                payment.getAmount(),
                capture.amount()
            );

        boolean sameExternalReference =
            Objects.equals(
                payment.getExternalReference(),
                capture.externalReference()
            );

        boolean samePosSession =
            Objects.equals(
                payment.getPosSessionId(),
                capture.posSessionId()
            );

        if (!sameOrder) {
            throw idempotencyPayloadConflict();
        }

        if (!sameMethod) {
            throw idempotencyPayloadConflict();
        }

        if (!sameRequestedAmount) {
            throw idempotencyPayloadConflict();
        }

        if (!sameExternalReference) {
            throw idempotencyPayloadConflict();
        }

        if (!samePosSession) {
            throw idempotencyPayloadConflict();
        }

        boolean samePersistedAmount =
            sameAmount(
                payment.getAmount(),
                existingOrder.getTotal()
            );

        if (!samePersistedAmount) {
            throw new PaymentConflictException(
                "Persisted payment does not match the order total."
            );
        }

        boolean samePersistedCurrency =
            sameCurrency(
                payment.getCurrency(),
                existingOrder.getCurrency()
            );

        if (!samePersistedCurrency) {
            throw new PaymentConflictException(
                "Persisted payment does not match the order currency."
            );
        }

        if (
            payment.getStatus()
                != PaymentStatus.COMPLETED
        ) {
            throw new PaymentConflictException(
                "Idempotent payment attempt is not completed."
            );
        }


        if (
            existingOrder.getPaymentStatus()
                != OrderPaymentStatus.COMPLETED
        ) {
            throw new PaymentConflictException(
                "Completed payment is not synchronized with order payment status."
            );
        }

        return result(
            payment,
            true
        );
    }

    private PaymentConflictException
        idempotencyPayloadConflict() {

        return new PaymentConflictException(
            PaymentErrorCode.IDEMPOTENCY_CONFLICT,
            "Idempotency key is already used with a different payment request."
        );
    }

    private void validatePayableOrder(
        Order order,
        OffsetDateTime now
    ) {

        boolean alreadyPaid =
            order.getStatus()
                == OrderStatus.PAID;

        if (alreadyPaid) {
            throw new PaymentConflictException(
                PaymentErrorCode.PAYMENT_ALREADY_COMPLETED,
                "Order payment is already completed."
            );
        }

        boolean paymentAlreadyCompleted =
            order.getPaymentStatus()
                == OrderPaymentStatus.COMPLETED;

        if (paymentAlreadyCompleted) {
            throw new PaymentConflictException(
                PaymentErrorCode.PAYMENT_ALREADY_COMPLETED,
                "Order payment is already completed."
            );
        }

        boolean awaitingPayment =
            order.getStatus()
                == OrderStatus.AWAITING_PAYMENT;

        if (!awaitingPayment) {
            throw new PaymentConflictException(
                PaymentErrorCode.INVALID_ORDER_STATUS,
                "Order is not awaiting payment."
            );
        }

        boolean pendingPayment =
            order.getPaymentStatus()
                == OrderPaymentStatus.PENDING;

        if (!pendingPayment) {
            throw new PaymentConflictException(
                PaymentErrorCode.INVALID_ORDER_STATUS,
                "Order is not awaiting payment."
            );
        }

        OffsetDateTime expiresAt =
            order.getPaymentExpiresAt();

        if (expiresAt == null) {
            throw new PaymentConflictException(
                PaymentErrorCode.ORDER_EXPIRED,
                "Order payment window has expired."
            );
        }

        boolean stillPayable =
            now.isBefore(
                expiresAt
            );

        if (!stillPayable) {
            throw new PaymentConflictException(
                PaymentErrorCode.ORDER_EXPIRED,
                "Order payment window has expired."
            );
        }

        BigDecimal total =
            order.getTotal();

        if (total == null) {
            throw new PaymentConflictException(
                "Order total must be positive before payment."
            );
        }

        if (total.signum() <= 0) {
            throw new PaymentConflictException(
                "Order total must be positive before payment."
            );
        }

        String currency =
            normalizeCurrency(
                order.getCurrency()
            );

        if (currency == null) {
            throw new PaymentConflictException(
                "Order currency is invalid."
            );
        }
    }

    private NormalizedCapture normalize(
        PaymentCaptureCommand command
    ) {

        if (command == null) {
            throw new PaymentValidationException(
                "Payment request is required."
            );
        }

        PaymentMethod method =
            command.method();

        boolean cash =
            method == PaymentMethod.CASH;

        boolean card =
            method == PaymentMethod.CARD_TPE;

        boolean supported =
            cash;

        if (card) {
            supported =
                true;
        }

        if (!supported) {
            throw new PaymentValidationException(
                "Only CASH and CARD_TPE are supported in the MVP."
            );
        }

        String idempotencyKey =
            normalizeRequired(
                command.idempotencyKey(),
                "Idempotency key",
                IDEMPOTENCY_KEY_MAX_LENGTH
            );

        if (
            idempotencyKey.length()
                < 8
        ) {
            throw new PaymentValidationException(
                "Idempotency key must contain at least 8 characters."
            );
        }

        BigDecimal amount =
            normalizePaymentAmount(
                command.amount()
            );

        String externalReference =
            normalizeOptional(
                command.externalReference(),
                160,
                "External reference"
            );

        return new NormalizedCapture(
            method,
            idempotencyKey,
            amount,
            externalReference,
            command.posSessionId()
        );
    }

    private BigDecimal normalizePaymentAmount(
        BigDecimal value
    ) {

        if (value == null) {
            throw new PaymentValidationException(
                "Payment amount is required."
            );
        }

        if (
            value.signum()
                <= 0
        ) {
            throw new PaymentValidationException(
                "Payment amount must be positive."
            );
        }

        BigDecimal normalized;

        try {

            normalized =
                value.setScale(
                    2,
                    RoundingMode.UNNECESSARY
                );

        } catch (
            ArithmeticException exception
        ) {
            throw new PaymentValidationException(
                "Payment amount supports at most two decimal places."
            );
        }

        if (normalized.precision() > 12) {
            throw new PaymentValidationException(
                "Payment amount is too large."
            );
        }

        return normalized;
    }

    private void validateRequestedAmount(
        BigDecimal requestedAmount,
        BigDecimal expectedAmount
    ) {

        if (
            requestedAmount.compareTo(
                expectedAmount
            ) != 0
        ) {
            throw new PaymentValidationException(
                PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH,
                "Payment amount does not match the order total."
            );
        }
    }

    private void validatePosSession(
        UUID posSessionId,
        UUID organizationId
    ) {

        if (posSessionId == null) {
            return;
        }

        List<UUID> matchingSessions =
            jdbcTemplate.query(
                """
                select ps.id
                from pos_sessions ps
                join pos_terminals pt
                  on pt.id = ps.terminal_id
                join locations l
                  on l.id = pt.location_id
                join campuses c
                  on c.id = l.campus_id
                where ps.id = ?
                  and c.organization_id = ?
                  and ps.status = 'OPEN'
                for share of ps
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    resultSet.getObject(
                        "id",
                        UUID.class
                    ),
                posSessionId,
                organizationId
            );

        if (matchingSessions.isEmpty()) {
            throw new PaymentConflictException(
                PaymentErrorCode.POS_SESSION_NOT_OPEN,
                "POS session does not exist, is closed, or is outside the organization."
            );
        }
    }

    private String normalizeRequired(
        String value,
        String field,
        int maxLength
    ) {

        String normalized =
            normalizeOptional(
                value,
                maxLength,
                field
            );

        if (normalized == null) {
            throw new PaymentValidationException(
                field + " is required."
            );
        }

        return normalized;
    }

    private String normalizeOptional(
        String value,
        int maxLength,
        String field
    ) {

        if (value == null) {
            return null;
        }

        String normalized =
            value.trim();

        if (normalized.isEmpty()) {
            return null;
        }

        if (
            normalized.length()
                > maxLength
        ) {
            throw new PaymentValidationException(
                field + " is too long."
            );
        }

        return normalized;
    }

    private String normalizeCurrency(
        String value
    ) {

        if (value == null) {
            return null;
        }

        String normalized =
            value.trim();

        if (normalized.length() != 3) {
            return null;
        }

        return normalized;
    }

    private boolean sameCurrency(
        String first,
        String second
    ) {

        String firstNormalized =
            normalizeCurrency(
                first
            );

        String secondNormalized =
            normalizeCurrency(
                second
            );

        return Objects.equals(
            firstNormalized,
            secondNormalized
        );
    }

    private boolean sameAmount(
        BigDecimal first,
        BigDecimal second
    ) {

        if (first == null) {
            return second == null;
        }

        if (second == null) {
            return false;
        }

        return first.compareTo(
            second
        ) == 0;
    }

    private User authenticatedUser(
        UUID actorId
    ) {

        if (actorId == null) {
            throw new BadCredentialsException(
                "Authenticated user does not exist."
            );
        }

        User actor =
            userRepository
                .findById(
                    actorId
                )
                .orElseThrow(() ->
                    new BadCredentialsException(
                        "Authenticated user does not exist."
                    )
                );

        if (
            !actor
                .getOrganization()
                .isActive()
        ) {
            throw new PaymentConflictException(
                "Organization is inactive."
            );
        }

        return actor;
    }

    private void lockIdempotencyKey(
        String idempotencyKey
    ) {

        UUID lockUuid =
            UUID.nameUUIDFromBytes(
                (
                    "PAYMENT:"
                    + idempotencyKey
                ).getBytes(
                    StandardCharsets.UTF_8
                )
            );

        long lockKey =
            lockUuid.getMostSignificantBits()
            ^ lockUuid.getLeastSignificantBits();

        jdbcTemplate.query(
            "SELECT pg_advisory_xact_lock(?)",
            statement ->
                statement.setLong(
                    1,
                    lockKey
                ),
            (ResultSetExtractor<Void>)
                resultSet -> null
        );
    }

    private PaymentCaptureResult result(
        Payment payment,
        boolean replayed
    ) {

        String currency =
            payment.getCurrency();

        if (currency != null) {
            currency =
                currency.trim();
        }

        return new PaymentCaptureResult(
            payment.getId(),
            payment
                .getOrder()
                .getId(),
            payment.getPosSessionId(),
            payment.getMethod(),
            payment.getStatus(),
            payment.getAmount(),
            currency,
            payment.getExternalReference(),
            payment.getPaidAt(),
            replayed
        );
    }

    private record NormalizedCapture(
        PaymentMethod method,
        String idempotencyKey,
        BigDecimal amount,
        String externalReference,
        UUID posSessionId
    ) {
    }
}
