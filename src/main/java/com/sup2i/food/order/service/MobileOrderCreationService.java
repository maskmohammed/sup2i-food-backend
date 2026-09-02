package com.sup2i.food.order.service;

import com.sup2i.food.order.api.dto.CreateOrderItemRequest;
import com.sup2i.food.order.api.dto.CreateOrderRequest;
import com.sup2i.food.order.api.dto.OrderItemResponse;
import com.sup2i.food.order.api.dto.OrderMutationResponse;
import com.sup2i.food.order.api.dto.OrderResponse;
import com.sup2i.food.order.api.dto.UpsertOrderItemRequest;
import com.sup2i.food.order.api.dto.UpsertOrderRequest;
import com.sup2i.food.order.exception.OrderConflictException;
import com.sup2i.food.order.exception.OrderValidationException;

import jakarta.persistence.EntityManager;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class MobileOrderCreationService {

    private static final String RESOURCE_TYPE =
        "ORDER_CREATE";

    private static final int RESPONSE_STATUS_CREATED =
        201;

    private static final Duration IDEMPOTENCY_RETENTION =
        Duration.ofHours(24);

    private final OrderService orderService;

    private final JdbcTemplate jdbcTemplate;

    private final EntityManager entityManager;

    public MobileOrderCreationService(
        OrderService orderService,
        JdbcTemplate jdbcTemplate,
        EntityManager entityManager
    ) {
        this.orderService =
            orderService;

        this.jdbcTemplate =
            jdbcTemplate;

        this.entityManager =
            entityManager;
    }

    @Transactional
    public OrderResponse create(
        UUID actorId,
        String rawIdempotencyKey,
        CreateOrderRequest request
    ) {
        if (request == null) {
            throw new OrderValidationException(
                "Order request is required."
            );
        }

        if (request.locationId() == null) {
            throw new OrderValidationException(
                "locationId is required."
            );
        }

        if (
            request.items() == null
            || request.items().isEmpty()
        ) {
            throw new OrderValidationException(
                "Order must contain at least one item."
            );
        }

        UUID organizationId =
            organizationId(
                actorId
            );

        String idempotencyKey =
            normalizeIdempotencyKey(
                rawIdempotencyKey
            );

        String scope =
            "ORDER_CREATE:"
                + organizationId;

        String requestHash =
            requestHash(
                actorId,
                request
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

        Optional<StoredIdempotency> existing =
            stored(
                scope,
                idempotencyKey
            );

        if (existing.isPresent()) {
            StoredIdempotency replay =
                existing.get();

            validateReplay(
                replay,
                actorId,
                requestHash
            );

            entityManager.clear();

            return orderService.find(
                actorId,
                replay.resourceId()
            );
        }

        UUID orderId =
            UUID.randomUUID();

        List<UpsertOrderItemRequest> baseItems =
            new ArrayList<>();

        for (CreateOrderItemRequest item : request.items()) {
            if (item == null) {
                throw new OrderValidationException(
                    "Order item cannot be null."
                );
            }

            if (item.productId() == null) {
                throw new OrderValidationException(
                    "productId is required."
                );
            }

            if (
                item.quantity() < 1
                || item.quantity() > 99
            ) {
                throw new OrderValidationException(
                    "Order item quantity must be between 1 and 99."
                );
            }

            baseItems.add(
                new UpsertOrderItemRequest(
                    item.productId(),
                    item.variantId(),
                    item.quantity(),
                    normalizeText(
                        item.specialInstructions()
                    )
                )
            );
        }

        UpsertOrderRequest baseRequest =
            new UpsertOrderRequest(
                request.locationId(),
                request.slotId(),
                "MAD",
                null,
                List.copyOf(
                    baseItems
                )
            );

        OrderMutationResponse mutation =
            orderService.upsertDraft(
                actorId,
                orderId,
                baseRequest
            );

        if (
            mutation == null
            || mutation.order() == null
        ) {
            throw new IllegalStateException(
                "Canonical draft pipeline returned no order."
            );
        }

        if (!orderId.equals(mutation.order().id())) {
            throw new IllegalStateException(
                "Canonical draft pipeline returned an unexpected order."
            );
        }

        List<OrderItemResponse> available =
            new ArrayList<>(
                mutation.order().items()
            );

        if (available.size() != request.items().size()) {
            throw new IllegalStateException(
                "Persisted order item count does not match request."
            );
        }

        for (CreateOrderItemRequest requested : request.items()) {
            OrderItemResponse persisted =
                takeMatchingItem(
                    available,
                    requested
                );

            List<OptionRow> options =
                validateOptions(
                    requested.productId(),
                    requested.optionIds()
                );

            BigDecimal optionDelta =
                BigDecimal.ZERO
                    .setScale(2);

            for (OptionRow option : options) {
                optionDelta =
                    optionDelta.add(
                        option.priceDelta()
                    );
            }

            optionDelta =
                money(
                    optionDelta
                );

            BigDecimal adjustedUnitPrice =
                money(
                    persisted
                        .unitPrice()
                        .add(
                            optionDelta
                        )
                );

            if (
                adjustedUnitPrice.compareTo(
                    BigDecimal.ZERO
                ) < 0
            ) {
                throw new OrderConflictException(
                    "Configured product options produce a negative price."
                );
            }

            BigDecimal adjustedLineTotal =
                money(
                    adjustedUnitPrice.multiply(
                        BigDecimal.valueOf(
                            persisted.quantity()
                        )
                    )
                );

            int lineUpdated =
                jdbcTemplate.update(
                    """
                    UPDATE order_items
                    SET unit_price = ?,
                        line_total = ?
                    WHERE id = ?
                      AND order_id = ?
                    """,
                    adjustedUnitPrice,
                    adjustedLineTotal,
                    persisted.id(),
                    orderId
                );

            if (lineUpdated != 1) {
                throw new IllegalStateException(
                    "Order item option pricing update failed."
                );
            }

            for (OptionRow option : options) {
                int inserted =
                    jdbcTemplate.update(
                        """
                        INSERT INTO order_item_options (
                            id,
                            order_item_id,
                            product_option_id,
                            option_name_snapshot,
                            price_delta,
                            quantity
                        )
                        VALUES (
                            ?,
                            ?,
                            ?,
                            ?,
                            ?,
                            1
                        )
                        """,
                        UUID.randomUUID(),
                        persisted.id(),
                        option.id(),
                        option.name(),
                        option.priceDelta()
                    );

                if (inserted != 1) {
                    throw new IllegalStateException(
                        "Order item option snapshot insert failed."
                    );
                }
            }
        }

        if (!available.isEmpty()) {
            throw new IllegalStateException(
                "Unmatched persisted order item remains."
            );
        }

        recalculateOrderTotals(
            organizationId,
            orderId
        );

        auditCreate(
            organizationId,
            actorId,
            orderId,
            request
        );

        persistIdempotency(
            scope,
            idempotencyKey,
            actorId,
            requestHash,
            orderId,
            now.plus(
                IDEMPOTENCY_RETENTION
            )
        );

        entityManager.clear();

        return orderService.find(
            actorId,
            orderId
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

    private OrderItemResponse takeMatchingItem(
        List<OrderItemResponse> available,
        CreateOrderItemRequest requested
    ) {
        String instructions =
            normalizeText(
                requested.specialInstructions()
            );

        for (
            int index = 0;
            index < available.size();
            index++
        ) {
            OrderItemResponse candidate =
                available.get(
                    index
                );

            boolean sameProduct =
                requested.productId().equals(
                    candidate.productId()
                );

            boolean sameVariant =
                Objects.equals(
                    requested.variantId(),
                    candidate.variantId()
                );

            boolean sameQuantity =
                requested.quantity()
                    == candidate.quantity();

            boolean sameInstructions =
                Objects.equals(
                    instructions,
                    candidate.specialInstructions()
                );

            boolean matches =
                sameProduct
                    && sameVariant
                    && sameQuantity
                    && sameInstructions;

            if (matches) {
                available.remove(
                    index
                );

                return candidate;
            }
        }

        throw new IllegalStateException(
            "Unable to match persisted order item with requested item."
        );
    }

    private List<OptionRow> validateOptions(
        UUID productId,
        List<UUID> rawOptionIds
    ) {
        List<UUID> optionIds =
            normalizeOptionIds(
                rawOptionIds
            );

        List<OptionGroupRow> groups =
            jdbcTemplate.query(
                """
                SELECT
                    id,
                    min_select,
                    max_select,
                    required
                FROM product_option_groups
                WHERE product_id = ?
                ORDER BY id
                """,
                (resultSet, rowNumber) ->
                    new OptionGroupRow(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getInt(
                            "min_select"
                        ),
                        resultSet.getInt(
                            "max_select"
                        ),
                        resultSet.getBoolean(
                            "required"
                        )
                    ),
                productId
            );

        List<OptionRow> options =
            new ArrayList<>();

        Map<UUID, Integer> groupCounts =
            new HashMap<>();

        for (UUID optionId : optionIds) {
            List<OptionRow> rows =
                jdbcTemplate.query(
                    """
                    SELECT
                        po.id,
                        po.name,
                        po.price_delta,
                        pog.id AS option_group_id
                    FROM product_options po
                    JOIN product_option_groups pog
                      ON pog.id = po.option_group_id
                    WHERE po.id = ?
                      AND pog.product_id = ?
                      AND po.is_active = TRUE
                    """,
                    (resultSet, rowNumber) ->
                        new OptionRow(
                            resultSet.getObject(
                                "id",
                                UUID.class
                            ),
                            resultSet.getObject(
                                "option_group_id",
                                UUID.class
                            ),
                            resultSet.getString(
                                "name"
                            ),
                            money(
                                resultSet.getBigDecimal(
                                    "price_delta"
                                )
                            )
                        ),
                    optionId,
                    productId
                );

            if (rows.size() != 1) {
                throw new OrderValidationException(
                    "Selected product option does not exist, is inactive, or belongs to another product."
                );
            }

            OptionRow option =
                rows.getFirst();

            options.add(
                option
            );

            int current =
                groupCounts.getOrDefault(
                    option.groupId(),
                    0
                );

            groupCounts.put(
                option.groupId(),
                current + 1
            );
        }

        for (OptionGroupRow group : groups) {
            int selected =
                groupCounts.getOrDefault(
                    group.id(),
                    0
                );

            int minimum =
                group.minSelect();

            if (
                group.required()
                && minimum < 1
            ) {
                minimum =
                    1;
            }

            if (selected < minimum) {
                throw new OrderValidationException(
                    "Required product option selection is missing."
                );
            }

            if (selected > group.maxSelect()) {
                throw new OrderValidationException(
                    "Too many options selected for a product option group."
                );
            }
        }

        return List.copyOf(
            options
        );
    }

    private List<UUID> normalizeOptionIds(
        List<UUID> rawOptionIds
    ) {
        if (
            rawOptionIds == null
            || rawOptionIds.isEmpty()
        ) {
            return List.of();
        }

        LinkedHashSet<UUID> unique =
            new LinkedHashSet<>();

        for (UUID optionId : rawOptionIds) {
            if (optionId == null) {
                throw new OrderValidationException(
                    "optionIds cannot contain null."
                );
            }

            if (!unique.add(optionId)) {
                throw new OrderValidationException(
                    "optionIds cannot contain duplicate values."
                );
            }
        }

        return List.copyOf(
            unique
        );
    }

    private void recalculateOrderTotals(
        UUID organizationId,
        UUID orderId
    ) {
        List<OrderTotals> rows =
            jdbcTemplate.query(
                """
                SELECT
                    COALESCE(
                        SUM(line_total),
                        0
                    ) AS subtotal,
                    COALESCE(
                        SUM(line_tax),
                        0
                    ) AS tax_total
                FROM order_items
                WHERE order_id = ?
                """,
                (resultSet, rowNumber) ->
                    new OrderTotals(
                        money(
                            resultSet.getBigDecimal(
                                "subtotal"
                            )
                        ),
                        money(
                            resultSet.getBigDecimal(
                                "tax_total"
                            )
                        )
                    ),
                orderId
            );

        if (rows.size() != 1) {
            throw new IllegalStateException(
                "Unable to calculate order totals."
            );
        }

        OrderTotals totals =
            rows.getFirst();

        int updated =
            jdbcTemplate.update(
                """
                UPDATE orders
                SET subtotal = ?,
                    tax_total = ?,
                    total = GREATEST(
                        ? - discount_total,
                        0
                    ),
                    version = version + 1,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND organization_id = ?
                  AND status = 'DRAFT'
                """,
                totals.subtotal(),
                totals.taxTotal(),
                totals.subtotal(),
                orderId,
                organizationId
            );

        if (updated != 1) {
            throw new IllegalStateException(
                "Order total recalculation update failed."
            );
        }
    }

    private void auditCreate(
        UUID organizationId,
        UUID actorId,
        UUID orderId,
        CreateOrderRequest request
    ) {
        int optionCount =
            0;

        for (CreateOrderItemRequest item : request.items()) {
            if (item.optionIds() != null) {
                optionCount +=
                    item.optionIds().size();
            }
        }

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
                    source,
                    result
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    'MOBILE_ORDER_CREATED',
                    'ORDER',
                    ?,
                    jsonb_build_object(
                        'orderId',
                        ?::text,
                        'locationId',
                        ?::text,
                        'itemCount',
                        ?,
                        'optionCount',
                        ?
                    ),
                    'API',
                    'SUCCESS'
                )
                """,
                UUID.randomUUID(),
                organizationId,
                actorId,
                orderId,
                orderId,
                request.locationId(),
                request.items().size(),
                optionCount
            );

        if (inserted != 1) {
            throw new IllegalStateException(
                "Mobile order creation audit insert failed."
            );
        }
    }

    private String normalizeIdempotencyKey(
        String raw
    ) {
        if (raw == null) {
            throw new OrderValidationException(
                "Idempotency-Key header is required."
            );
        }

        String value =
            raw.trim();

        if (value.length() < 8) {
            throw new OrderValidationException(
                "Idempotency-Key must contain at least 8 characters."
            );
        }

        if (value.length() > 160) {
            throw new OrderValidationException(
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
                "ORDER_CREATE_IDEMPOTENCY"
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
                "Order creation idempotency lookup returned multiple rows."
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
            throw new OrderConflictException(
                "Idempotency-Key was already used for another order request."
            );
        }
    }

    private void persistIdempotency(
        String scope,
        String key,
        UUID actorId,
        String requestHash,
        UUID orderId,
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
                        'orderId',
                        ?::text
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
                orderId,
                RESOURCE_TYPE,
                orderId,
                expiresAt
            );

        if (inserted != 1) {
            throw new IllegalStateException(
                "Order creation idempotency insert failed."
            );
        }
    }

    private String requestHash(
        UUID actorId,
        CreateOrderRequest request
    ) {
        StringBuilder canonical =
            new StringBuilder();

        canonical.append(
            "ORDER_CREATE"
        );

        canonical.append(
            '\n'
        );

        canonical.append(
            actorId
        );

        canonical.append(
            '\n'
        );

        canonical.append(
            request.locationId()
        );

        canonical.append(
            '\n'
        );

        canonical.append(
            request.slotId() == null
                ? ""
                : request.slotId()
        );

        int lineNumber =
            0;

        for (CreateOrderItemRequest item : request.items()) {
            canonical.append(
                '\n'
            );

            canonical.append(
                lineNumber
            );

            canonical.append(
                '|'
            );

            canonical.append(
                item.productId()
            );

            canonical.append(
                '|'
            );

            canonical.append(
                item.variantId() == null
                    ? ""
                    : item.variantId()
            );

            canonical.append(
                '|'
            );

            canonical.append(
                item.quantity()
            );

            canonical.append(
                '|'
            );

            canonical.append(
                normalizeText(
                    item.specialInstructions()
                )
            );

            List<String> sortedOptions =
                new ArrayList<>();

            if (item.optionIds() != null) {
                for (UUID optionId : item.optionIds()) {
                    sortedOptions.add(
                        optionId == null
                            ? "NULL"
                            : optionId.toString()
                    );
                }
            }

            sortedOptions.sort(
                String::compareTo
            );

            for (String option : sortedOptions) {
                canonical.append(
                    '|'
                );

                canonical.append(
                    option
                );
            }

            lineNumber++;
        }

        byte[] digest =
            sha256Bytes(
                canonical.toString()
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

    private String normalizeText(
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

    private record OptionRow(
        UUID id,
        UUID groupId,
        String name,
        BigDecimal priceDelta
    ) {
    }

    private record OptionGroupRow(
        UUID id,
        int minSelect,
        int maxSelect,
        boolean required
    ) {
    }

    private record OrderTotals(
        BigDecimal subtotal,
        BigDecimal taxTotal
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