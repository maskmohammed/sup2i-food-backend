package com.sup2i.food.pos.service;

import com.sup2i.food.order.api.dto.OrderResponse;
import com.sup2i.food.order.api.dto.UpsertOrderItemRequest;
import com.sup2i.food.order.api.dto.UpsertOrderRequest;
import com.sup2i.food.order.service.OrderService;
import com.sup2i.food.order.service.PosDirectQuote;
import com.sup2i.food.pos.api.dto.PosSaleItemRequest;
import com.sup2i.food.pos.api.dto.PosSaleQuoteLineResponse;
import com.sup2i.food.pos.api.dto.PosSaleQuoteResponse;
import com.sup2i.food.pos.api.dto.PosSaleRequest;
import com.sup2i.food.pos.exception.PosErrorCode;
import com.sup2i.food.pos.exception.PosException;
import com.sup2i.food.pos.exception.PosValidationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PosSaleService {

    private static final int
        IDEMPOTENCY_KEY_MIN_LENGTH =
            8;

    private static final int
        IDEMPOTENCY_KEY_MAX_LENGTH =
            160;

    private static final int
        CREATE_RESPONSE_STATUS =
            201;

    private static final String
        RESOURCE_TYPE =
            "POS_DIRECT_ORDER";

    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper;
    private final OrderService orderService;

    public PosSaleService(
        JdbcTemplate jdbcTemplate,
        JsonMapper jsonMapper,
        OrderService orderService
    ) {
        this.jdbcTemplate =
            jdbcTemplate;

        this.jsonMapper =
            jsonMapper;

        this.orderService =
            orderService;
    }

    @Transactional(readOnly = true)
    public PosSaleQuoteResponse quote(
        UUID actorId,
        PosSaleRequest request
    ) {

        UUID organizationId =
            organizationId(
                actorId
            );

        requireRequest(
            request
        );

        OwnedSession session =
            ownedOpenSession(
                request.posSessionId(),
                actorId,
                organizationId
            );

        PosDirectQuote quote =
            orderService
                .quotePosDirect(
                    actorId,
                    orderRequest(
                        session.locationId(),
                        request
                    )
                );

        List<PosSaleQuoteLineResponse> items =
            quote
                .lines()
                .stream()
                .map(line ->
                    new PosSaleQuoteLineResponse(
                        line.productId(),
                        line.variantId(),
                        line.productName(),
                        line.variantName(),
                        line.sku(),
                        line.unitPrice(),
                        line.quantity(),
                        line.discountAmount(),
                        line.lineTotal(),
                        line.taxRate(),
                        line.lineTax(),
                        line.specialInstructions()
                    )
                )
                .toList();

        return new PosSaleQuoteResponse(
            quote.subtotal(),
            quote.taxTotal(),
            quote.discountTotal(),
            quote.total(),
            quote.currency(),
            items
        );
    }

    @Transactional
    public OrderResponse create(
        UUID actorId,
        String rawIdempotencyKey,
        PosSaleRequest request
    ) {

        UUID organizationId =
            organizationId(
                actorId
            );

        requireRequest(
            request
        );

        String idempotencyKey =
            normalizeIdempotencyKey(
                rawIdempotencyKey
            );

        String scope =
            "POS_SALE_CREATE:"
                + organizationId;

        String requestHash =
            requestHash(
                request
            );

        lockIdempotencyKey(
            scope,
            idempotencyKey
        );

        deleteExpiredIdempotency(
            scope,
            idempotencyKey
        );

        Optional<StoredIdempotency> stored =
            findIdempotency(
                scope,
                idempotencyKey
            );

        if (stored.isPresent()) {

            return replay(
                stored.get(),
                actorId,
                requestHash
            );
        }

        OwnedSession session =
            lockOwnedOpenSession(
                request.posSessionId(),
                actorId,
                organizationId
            );

        OrderResponse response =
            orderService
                .createPosDirect(
                    actorId,
                    orderRequest(
                        session.locationId(),
                        request
                    )
                );

        response =
            normalizeForIdempotency(
                response
            );

        persistIdempotency(
            scope,
            idempotencyKey,
            actorId,
            requestHash,
            response
        );

        return response;
    }

    private void requireRequest(
        PosSaleRequest request
    ) {

        if (request == null) {
            throw new PosValidationException(
                "POS sale request is required."
            );
        }

        if (request.posSessionId() == null) {
            throw new PosValidationException(
                "posSessionId is required."
            );
        }

        if (
            request.items() == null
            || request.items().isEmpty()
        ) {
            throw new PosValidationException(
                "POS sale must contain at least one item."
            );
        }
    }

    private UpsertOrderRequest orderRequest(
        UUID locationId,
        PosSaleRequest request
    ) {

        List<UpsertOrderItemRequest> items =
            request
                .items()
                .stream()
                .map(this::orderItem)
                .toList();

        return new UpsertOrderRequest(
            locationId,
            "MAD",
            request.customerNote(),
            items
        );
    }

    private UpsertOrderItemRequest orderItem(
        PosSaleItemRequest item
    ) {

        return new UpsertOrderItemRequest(
            item.productId(),
            item.variantId(),
            item.quantity(),
            item.specialInstructions()
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

    private OwnedSession ownedOpenSession(
        UUID sessionId,
        UUID actorId,
        UUID organizationId
    ) {

        List<OwnedSession> sessions =
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
                this::mapSession,
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

    private OwnedSession lockOwnedOpenSession(
        UUID sessionId,
        UUID actorId,
        UUID organizationId
    ) {

        List<OwnedSession> sessions =
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
                FOR UPDATE OF ps
                """,
                this::mapSession,
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

    private OwnedSession mapSession(
        java.sql.ResultSet resultSet,
        int rowNumber
    ) throws java.sql.SQLException {

        return new OwnedSession(
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
        );
    }

    private String normalizeIdempotencyKey(
        String raw
    ) {

        if (raw == null) {
            throw new PosValidationException(
                "Idempotency-Key is required."
            );
        }

        String normalized =
            raw.trim();

        if (
            normalized.length()
                < IDEMPOTENCY_KEY_MIN_LENGTH
        ) {
            throw new PosValidationException(
                "Idempotency-Key must contain at least 8 characters."
            );
        }

        if (
            normalized.length()
                > IDEMPOTENCY_KEY_MAX_LENGTH
        ) {
            throw new PosValidationException(
                "Idempotency-Key cannot exceed 160 characters."
            );
        }

        return normalized;
    }

    private String requestHash(
        PosSaleRequest request
    ) {

        StringBuilder identity =
            new StringBuilder();

        appendField(
            identity,
            "POS_DIRECT"
        );

        appendField(
            identity,
            request
                .posSessionId()
                .toString()
        );

        appendField(
            identity,
            normalizeText(
                request.customerNote()
            )
        );

        for (
            PosSaleItemRequest item
            : request.items()
        ) {

            appendField(
                identity,
                item.productId()
                    .toString()
            );

            appendField(
                identity,
                item.variantId() == null
                    ? null
                    : item
                        .variantId()
                        .toString()
            );

            appendField(
                identity,
                Integer.toString(
                    item.quantity()
                )
            );

            appendField(
                identity,
                normalizeText(
                    item.specialInstructions()
                )
            );
        }

        return sha256(
            identity.toString()
        );
    }

    private void appendField(
        StringBuilder builder,
        String value
    ) {

        if (value == null) {

            builder.append(
                "-1:"
            );

            return;
        }

        builder
            .append(
                value.length()
            )
            .append(':')
            .append(value)
            .append('|');
    }

    private String normalizeText(
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

    private void lockIdempotencyKey(
        String scope,
        String key
    ) {

        UUID lockUuid =
            UUID.nameUUIDFromBytes(
                (
                    "POS:"
                    + scope
                    + ":"
                    + key
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

    private void deleteExpiredIdempotency(
        String scope,
        String key
    ) {

        jdbcTemplate.update(
            """
            DELETE FROM idempotency_records
            WHERE scope = ?
              AND idempotency_key = ?
              AND expires_at <= CURRENT_TIMESTAMP
            """,
            scope,
            key
        );
    }

    private Optional<StoredIdempotency>
        findIdempotency(
            String scope,
            String key
        ) {

        List<StoredIdempotency> records =
            jdbcTemplate.query(
                """
                SELECT
                    user_id,
                    request_hash,
                    response_status,
                    response_body::text AS response_body,
                    resource_type,
                    resource_id
                FROM idempotency_records
                WHERE scope = ?
                  AND idempotency_key = ?
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
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
                            "response_body"
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

        if (records.isEmpty()) {
            return Optional.empty();
        }

        if (records.size() != 1) {
            throw idempotencyConflict();
        }

        return Optional.of(
            records.get(0)
        );
    }

    private OrderResponse replay(
        StoredIdempotency stored,
        UUID actorId,
        String requestHash
    ) {

        boolean valid =
            actorId.equals(
                stored.userId()
            );

        if (valid) {
            valid =
                requestHash.equals(
                    stored.requestHash()
                );
        }

        if (valid) {
            valid =
                stored.responseStatus()
                    == CREATE_RESPONSE_STATUS;
        }

        if (valid) {
            valid =
                RESOURCE_TYPE.equals(
                    stored.resourceType()
                );
        }

        if (valid) {
            valid =
                stored.resourceId()
                    != null;
        }

        if (valid) {
            valid =
                stored.responseBody()
                    != null;
        }

        if (!valid) {
            throw idempotencyConflict();
        }

        OrderResponse response =
            deserialize(
                stored.responseBody()
            );

        if (
            !stored
                .resourceId()
                .equals(
                    response.id()
                )
        ) {
            throw idempotencyConflict();
        }

        return response;
    }

    private void persistIdempotency(
        String scope,
        String key,
        UUID actorId,
        String requestHash,
        OrderResponse response
    ) {

        String responseBody =
            serialize(
                response
            );

        int inserted =
            jdbcTemplate.update(
                """
                INSERT INTO idempotency_records (
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
                    CAST(? AS jsonb),
                    ?,
                    ?,
                    ?
                )
                """,
                key,
                scope,
                actorId,
                requestHash,
                CREATE_RESPONSE_STATUS,
                responseBody,
                RESOURCE_TYPE,
                response.id(),
                OffsetDateTime
                    .now()
                    .plusHours(24)
            );

        if (inserted != 1) {
            throw new IllegalStateException(
                "POS sale idempotency insert did not affect exactly one row."
            );
        }
    }

    private OrderResponse normalizeForIdempotency(
        OrderResponse response
    ) {

        return new OrderResponse(
            response.id(),
            response.orderNumber(),
            response.locationId(),
            response.studentId(),
            response.source(),
            response.status(),
            response.orderType(),
            response.paymentStatus(),
            response.subtotal(),
            response.taxTotal(),
            response.discountTotal(),
            response.total(),
            response.currency(),
            utc(
                response.paymentExpiresAt()
            ),
            response.customerNote(),
            response.version(),
            utc(
                response.createdAt()
            ),
            utc(
                response.updatedAt()
            ),
            response.items(),
            response.reservations()
        );
    }

    private OffsetDateTime utc(
        OffsetDateTime value
    ) {

        if (value == null) {
            return null;
        }

        return value.withOffsetSameInstant(
            java.time.ZoneOffset.UTC
        );
    }

    private String serialize(
        OrderResponse response
    ) {

        try {

            return jsonMapper.writeValueAsString(
                response
            );

        } catch (Exception exception) {

            throw new IllegalStateException(
                "Unable to serialize POS sale response.",
                exception
            );
        }
    }

    private OrderResponse deserialize(
        String body
    ) {

        try {

            return jsonMapper.readValue(
                body,
                OrderResponse.class
            );

        } catch (Exception exception) {

            throw idempotencyConflict();
        }
    }

    private PosException idempotencyConflict() {

        return new PosException(
            PosErrorCode.IDEMPOTENCY_CONFLICT,
            "Idempotency-Key is already used with a different POS sale request."
        );
    }

    private String sha256(
        String value
    ) {

        try {

            MessageDigest digest =
                MessageDigest.getInstance(
                    "SHA-256"
                );

            byte[] bytes =
                digest.digest(
                    value.getBytes(
                        StandardCharsets.UTF_8
                    )
                );

            StringBuilder result =
                new StringBuilder(
                    64
                );

            for (byte item : bytes) {

                String pair =
                    Integer.toHexString(
                        item & 0xff
                    );

                if (pair.length() == 1) {
                    result.append('0');
                }

                result.append(
                    pair
                );
            }

            return result.toString();

        } catch (
            NoSuchAlgorithmException exception
        ) {

            throw new IllegalStateException(
                "SHA-256 is unavailable.",
                exception
            );
        }
    }

    private record OwnedSession(
        UUID id,
        UUID terminalId,
        UUID locationId
    ) {
    }

    private record StoredIdempotency(
        UUID userId,
        String requestHash,
        int responseStatus,
        String responseBody,
        String resourceType,
        UUID resourceId
    ) {
    }
}